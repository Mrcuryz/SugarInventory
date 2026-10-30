from copy import deepcopy
from datetime import date, timedelta

import pytest
from app.daily_report_analysis import adapt_daily_report_analysis, daily_report_analysis_card, REPORT_ID
from app.goal_contracts import build_fact_envelope
from app.tools.client import MockToolClient
from app.tool_arguments import ToolArgumentBuilder
from app.state_models import WarehouseAgentState
from tests.test_llm_tool_loop import fixed_test_clock
from tests.test_llm_tool_loop import ScriptedLlmModel, main_delegate, call, final, runtime_for, chat_request

GOAL = 'PRODUCTION_DAILY_REPORT_ANALYSIS'
TOOL = 'run_registered_report'


def report(days=3):
    points = [dict(businessDate=(date(2026, 7, 1) + timedelta(days=i)).isoformat(), quantity=None if i == 0 else 0,
                   convertedTons=None, recordCount=int(i > 0), quantityRows=int(i > 0), tonnageRows=0, draftRows=0)
              for i in range(days)]
    return dict(dataScope='SAVED_PRODUCTION_DAILY_REPORT', reportDefinitionId=REPORT_ID, reportVersion=1,
                reportRunId='report_run_1234567890abcdef', reportName='生产日报分析', startDate='2026-07-01',
                endDate=points[-1]['businessDate'], dateRangeLabel='7月', filtersApplied={'departmentScope': '多晶车间'},
                productionDailyReport=dict(sourceLabel='生产日报', requestedDays=days, truncated=False, series=[dict(
                    summaryType='车间产量', departmentName='多晶车间', label='冰糖产量', unit='吨', quantity=0,
                    convertedTons=None, tonnageApplicable=True, recordCount=days-1, quantityRows=days-1, tonnageRows=0,
                    observedDays=days-1, tonnageObservedDays=0, draftRows=0, coverageLabel='覆盖不完整', points=points,
                    metricCode='POLY_OUTPUT', internalId=99)]), dataQuality=dict(partial=True, notes=['覆盖不完整']), limitations=[])


def test_adapter_preserves_null_zero_dates_and_backend_totals_without_internal_fields():
    raw = report()
    raw['productionDailyReport']['series'][0]['quantity'] = 100
    data = adapt_daily_report_analysis(raw)
    row = data['productionDailyReport']['series'][0]
    assert row['quantity'] == 100  # not recomputed from points
    assert [p['quantity'] for p in row['points']] == [None, 0, 0]
    assert 'POLY_OUTPUT' not in daily_report_analysis_card(data).model_dump_json()
    assert build_fact_envelope(goal_type=GOAL, tool_name=TOOL, arguments={}, safe_data=data, entity_contexts={}).status == 'AVAILABLE'


@pytest.mark.parametrize('fault', ['duplicate_date', 'missing_date', 'nan', 'scope', 'version'])
def test_invalid_authoritative_payload_rejected(fault):
    raw = report()
    row = raw['productionDailyReport']['series'][0]
    if fault == 'duplicate_date': row['points'][1]['businessDate'] = '2026-07-01'
    if fault == 'missing_date': row['points'].pop()
    if fault == 'nan': row['quantity'] = 'NaN'
    if fault == 'scope': raw['dataScope'] = 'ORDER'
    if fault == 'version': raw['reportVersion'] = 2
    with pytest.raises(ValueError): adapt_daily_report_analysis(raw)


def test_empty_and_all_null_differ_and_energy_has_no_tonnage():
    raw = report()
    raw['productionDailyReport']['series'][0].update(tonnageApplicable=False, convertedTons=999)
    data = adapt_daily_report_analysis(raw)
    assert data['productionDailyReport']['series'][0]['convertedTons'] is None
    raw['productionDailyReport']['series'] = []
    data = adapt_daily_report_analysis(raw)
    assert data['isEmpty']
    assert build_fact_envelope(goal_type=GOAL, tool_name=TOOL, arguments={}, safe_data=data, entity_contexts={}).status == 'NO_DATA'


def test_model_expert_tool_analysis_and_scope_memory():
    raw = report()
    model = ScriptedLlmModel([main_delegate('analytics_expert', GOAL), main_delegate('analytics_expert', GOAL)], [
        call(TOOL, dict(reportDefinitionId=REPORT_ID, reportVersion=1, startDate='2026-07-01', endDate='2026-07-03', departmentName='多晶车间')),
        final('已保存日报显示产量为0，覆盖不完整，缺值不等于0。', 'obs_1'),
        call(TOOL, dict(reportDefinitionId=REPORT_ID, reportVersion=1, startDate='2026-07-01', endDate='2026-07-03')),
        final('这是全部车间的日报趋势，包含已保存草稿。', 'obs_1')])
    tools = MockToolClient({TOOL: raw})
    runtime, store = runtime_for(model, tools, allowed_experts=('analytics_expert',))
    response = runtime.chat(chat_request('查看2026年7月1到3日多晶车间的日报产量趋势'))
    assert response.error is None
    assert response.cards[0].cardType == 'production_daily_report_analysis'
    assert response.reviewTrace['goalCompletion']['status'] == 'COMPLETE'
    runtime.chat(chat_request('改看全部车间'))
    context = model.expert_requests[-2].selectedContext['REPORT']
    assert context['startDate'] == '2026-07-01' and context['departmentName'] == '多晶车间'
    assert 'departmentName' not in tools.calls[-1]['arguments']
    assert store.get('agt_llm').last_report_context['departmentName'] is None


def test_trend_rejects_draft_exclusion_and_reanalyzes_without_requery():
    model = ScriptedLlmModel([main_delegate('analytics_expert', GOAL)], [
        call(TOOL, dict(reportDefinitionId=REPORT_ID, reportVersion=1, startDate='2026-07-01', endDate='2026-07-03')),
        final('未提交行未计入合计。', 'obs_1'),
        final('已保存草稿包含在日报合计中，未填报值不代表0。', 'obs_1'),
    ])
    tools = MockToolClient({TOOL: report()})
    runtime, _ = runtime_for(model, tools, allowed_experts=('analytics_expert',))
    response = runtime.chat(chat_request('查看2026年7月1到3日的日报产量趋势'))
    assert response.error is None
    assert '草稿包含' in response.answer
    assert len(tools.calls) == 1
    assert len(model.expert_requests) == 3
    assert model.expert_requests[-1].observations[-1]['status'] == 'PLAN_REJECTED'
    assert response.cards[0].cardType == 'production_daily_report_analysis'


def test_card_keeps_full_dates_while_model_samples_with_complete_totals():
    raw = report(31)
    raw['productionDailyReport']['series'] *= 10
    data = adapt_daily_report_analysis(raw)
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient({}))
    compact = runtime._compact_registered_report_model_data(data)
    row = compact['productionDailyReport']['series'][0]
    assert row['pointsTotalCount'] == 31 and len(row['points']) == 6
    assert row['points'][0]['quantity'] is None
    fields = daily_report_analysis_card(data).fields
    assert len(fields) < 100
    assert len(fields[1]['points']) == 31


@pytest.mark.parametrize('overrides', [
    {'comparisonMode': 'CUSTOM'}, {'taskType': 'IN'}, {'metricKey': 'sucrose'},
    {'endDate': '2026-07-22'}, {'startDate': '2026-06-01'},
    {'reportDefinitionId': 'daily_production_overview_v1', 'departmentName': '多晶车间'},
])
def test_daily_report_arguments_reject_unsupported_scope(overrides):
    builder = ToolArgumentBuilder(business_clock=fixed_test_clock())
    args = dict(reportDefinitionId=REPORT_ID, reportVersion=1, startDate='2026-07-01', endDate='2026-07-21')
    with pytest.raises(ValueError):
        builder.validate_llm_arguments(tool_name=TOOL, arguments={**args, **overrides},
                                       state=WarehouseAgentState(), user_message='查询指定范围')


def comparison(metric_count=2):
    return dict(comparisonMode='PREVIOUS_PERIOD', currentStartDate='2026-07-01', currentEndDate='2026-07-03',
                comparisonStartDate='2026-06-28', comparisonEndDate='2026-06-30', currentPeriodDays=3,
                comparisonPeriodDays=3, differentPeriodLengths=False, notes=['两期均来自已保存日报'],
                metrics=[dict(metricCode='INTERNAL_KEY', metricLabel=f'数量{i}', unit='件', additive=True,
                              currentValue=0, comparisonValue=20, absoluteChange=-20, percentChange=None,
                              note='覆盖不完整，不计算变化率', internalId=123) for i in range(metric_count)])


def test_comparison_retains_all_40_metrics_and_empty_current_is_not_no_data():
    raw = report(); raw['comparison'] = comparison(40); raw['productionDailyReport']['series'] = []
    data = adapt_daily_report_analysis(raw)
    assert not data['isEmpty'] and data['currentPeriodEmpty']
    assert build_fact_envelope(goal_type=GOAL, tool_name=TOOL, arguments={}, safe_data=data, entity_contexts={}).status == 'AVAILABLE'
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient({}))
    observed = runtime._compact_registered_report_model_data(data)['comparison']
    assert len(observed['metrics']) == 40
    assert observed['metrics'][-1]['currentValue'] == 0 and observed['metrics'][-1]['percentChange'] is None
    card = daily_report_analysis_card(data)
    assert len(card.fields[0]['comparison']['metrics']) == 40
    assert 'INTERNAL_KEY' not in card.model_dump_json() and 'internalId' not in card.model_dump_json()
    raw['comparison']['metrics'] = []
    assert adapt_daily_report_analysis(raw)['isEmpty']


@pytest.mark.parametrize('fault', ['length', 'overlap', 'current', 'adjacency', 'limit', 'infinite', 'zero_rate', 'negative_rate'])
def test_comparison_payload_rejects_invalid_evidence(fault):
    raw = report(); raw['comparison'] = comparison()
    value = raw['comparison']
    if fault == 'length': value['comparisonStartDate'] = '2026-06-27'
    if fault == 'overlap': value.update(comparisonStartDate='2026-06-30', comparisonEndDate='2026-07-02')
    if fault == 'current': value['currentStartDate'] = '2026-07-02'
    if fault == 'adjacency': value.update(comparisonStartDate='2026-06-27', comparisonEndDate='2026-06-29')
    if fault == 'limit': value['metrics'] *= 21
    if fault == 'infinite': value['metrics'][0]['currentValue'] = '1e9999'
    if fault == 'zero_rate': value['metrics'][0].update(comparisonValue=0, percentChange=100)
    if fault == 'negative_rate': value['metrics'][0].update(comparisonValue=-1, percentChange=100)
    with pytest.raises(ValueError): adapt_daily_report_analysis(raw)


@pytest.mark.parametrize('mode', ['PREVIOUS_PERIOD', 'CUSTOM'])
def test_comparison_arguments_allow_same_scope_equal_periods(mode):
    builder = ToolArgumentBuilder(business_clock=fixed_test_clock())
    args = dict(reportDefinitionId=REPORT_ID, reportVersion=1, startDate='2026-07-01', endDate='2026-07-03',
                departmentName='多晶车间', productQuery='黄冰糖', comparisonMode=mode)
    if mode == 'CUSTOM': args.update(comparisonStartDate='2026-06-01', comparisonEndDate='2026-06-03')
    result = builder.validate_llm_arguments(tool_name=TOOL, arguments=args, state=WarehouseAgentState(), user_message='比较指定日期')
    assert result == args


@pytest.mark.parametrize('start,end', [('2026-06-01', '2026-06-02'), ('2026-07-02', '2026-07-04'), ('2026-07-22', '2026-07-24')])
def test_daily_custom_comparison_rejects_unequal_overlap_future(start, end):
    builder = ToolArgumentBuilder(business_clock=fixed_test_clock())
    with pytest.raises(ValueError):
        builder.validate_llm_arguments(tool_name=TOOL, arguments=dict(reportDefinitionId=REPORT_ID, reportVersion=1,
            startDate='2026-07-01', endDate='2026-07-03', comparisonMode='CUSTOM', comparisonStartDate=start,
            comparisonEndDate=end), state=WarehouseAgentState(), user_message='比较指定日期')


def test_comparison_tool_loop_preserves_evidence_card_and_followup_context():
    raw = report(); raw['comparison'] = comparison()
    model = ScriptedLlmModel([main_delegate('analytics_expert', GOAL)], [
        call(TOOL, dict(reportDefinitionId=REPORT_ID, reportVersion=1, startDate='2026-07-01', endDate='2026-07-03',
                       comparisonMode='PREVIOUS_PERIOD', departmentName='多晶车间')),
        final('已保存日报含草稿，本期已记录0件，对比期20件；覆盖不完整，不计算变化率。', 'obs_1')])
    runtime, store = runtime_for(model, MockToolClient({TOOL: raw}), allowed_experts=('analytics_expert',))
    answer = runtime.chat(chat_request('比较2026年7月1至3日与上一期间的日报产量'))
    assert answer.error is None
    assert answer.reviewTrace['goalCompletion']['status'] == 'COMPLETE'
    assert answer.cards[0].fields[0]['comparison']['metrics'][0]['absoluteChange'] == -20
    assert model.expert_requests[-1].selectedContext['REPORT']['comparisonMode'] == 'PREVIOUS_PERIOD'
    assert store.get('agt_llm').last_report_context['comparisonMode'] == 'PREVIOUS_PERIOD'
