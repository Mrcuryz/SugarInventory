from app.tools.client import MockToolClient
from tests.test_llm_tool_loop import ScriptedLlmModel, main_delegate, call, final, runtime_for, chat_request


def test_exact_day_query_corrects_wrong_list_tool_without_auto_routing_or_writes():
    model = ScriptedLlmModel([main_delegate('assay_expert', 'PRODUCT_ASSAY_REPORT')], [
        call('resolve_products', {'query': '验收糖'}),
        call('query_assay_records', {'productScope': {'type': 'SINGLE_PRODUCT', 'productRef': 'CURRENT_PRODUCT'},
                                    'dateRange': {'type': 'EXACT', 'date': '2026-09-25'}}),
        call('get_assay_status', {'productRef': 'CURRENT_PRODUCT', 'productionDate': '2026-09-25'}),
        final('该产品2026-09-25的化验不合格，色值10，pH值0。', 'obs_3')])
    tools = MockToolClient({'resolve_products': {'resolutionStatus': 'UNIQUE', 'candidates': [
        {'productId': 178, 'productName': '验收糖', 'displayLabel': '验收糖'}]},
        'get_assay_status': {'judgeResult': 'FAIL', 'assay': {'productName': '验收糖',
            'sampleDate': '2026-09-25', 'judgeResult': 'FAIL', 'colorValue': 10, 'phValue': 0}}})
    runtime, _ = runtime_for(model, tools)
    response = runtime.chat(chat_request('查询验收糖在2026-09-25的化验记录，只查询已有记录，不录入。'))
    assert response.error is None, response.model_dump()
    assert '不合格' in response.answer
    assert [item['toolName'] for item in tools.calls] == ['resolve_products', 'get_assay_status']
    correction = model.expert_requests[2].observations[-1]
    assert correction['status'] == 'PLAN_REJECTED'
    assert 'get_assay_status' in correction['message']
    assert 'productionDate' in correction['message']
    assert all(card.cardType != 'assay_entry_preview' for card in response.cards)


def test_registered_history_goal_can_query_an_exact_day_without_report_tool_conflict():
    from app.graph.state import SelectedEntity, WarehouseAgentState
    from app.tool_arguments import ToolArgumentBuilder
    state = WarehouseAgentState(active_goal_type='PRODUCT_ASSAY_HISTORY', goal_contract_locked=True)
    state.selected_product = SelectedEntity(178, '验收糖', 'resolver', {'scopeType': 'SINGLE_PRODUCT'})
    value = ToolArgumentBuilder().validate_llm_arguments(tool_name='query_assay_records', state=state,
        user_message='查询验收糖在2026-09-25的化验记录，只查询已有记录，不录入。',
        arguments={'productScope': {'type': 'SINGLE_PRODUCT', 'productRef': 'CURRENT_PRODUCT'},
                   'dateRange': {'type': 'EXACT', 'date': '2026-09-25'}})
    assert value['productScope'] == {'type': 'SINGLE_PRODUCT', 'productId': 178}
    assert value['dateRange'] == {'type': 'EXACT', 'date': '2026-09-25'}


def test_unresolved_name_group_is_rejected_then_expert_resolves_product_before_readback():
    name = '化验组验收乙糖'
    model = ScriptedLlmModel([main_delegate('assay_expert', 'PRODUCT_ASSAY_REPORT')], [
        call('query_assay_records', {'productScope': {'type': 'EXACT_PRODUCT_NAME_GROUP', 'productName': '验收乙糖'},
                                    'dateRange': {'type': 'EXACT', 'date': '2026-09-25'}}),
        call('resolve_products', {'query': name}),
        call('get_assay_status', {'productRef': 'CURRENT_PRODUCT', 'productionDate': '2026-09-25'}),
        final('该产品2026-09-25的化验不合格，色值10。', 'obs_3')])
    tools = MockToolClient({'resolve_products': {'resolutionStatus': 'UNIQUE', 'candidates': [
        {'productId': 178, 'productName': name, 'displayLabel': name}]},
        'get_assay_status': {'judgeResult': 'FAIL', 'assay': {'productName': name,
            'sampleDate': '2026-09-25', 'judgeResult': 'FAIL', 'colorValue': 10}}})
    runtime, _ = runtime_for(model, tools)
    response = runtime.chat(chat_request(f'查询{name}在2026-09-25的化验记录，只查询已有记录，不录入。'))
    assert response.error is None, response.model_dump()
    assert [item['toolName'] for item in tools.calls] == ['resolve_products', 'get_assay_status']
    assert tools.calls[0]['arguments']['query'] == name
    assert 'resolve_products' in model.expert_requests[1].observations[-1]['message']
    assert '不合格' in response.answer


def test_assay_name_group_requires_explicit_scope_not_merely_all_versions():
    import pytest
    from app.graph.state import SelectedEntity, WarehouseAgentState
    from app.tool_arguments import ToolArgumentBuilder
    builder = ToolArgumentBuilder()
    state = WarehouseAgentState(active_goal_type='PRODUCT_ASSAY_HISTORY', goal_contract_locked=True)
    args = {'productScope': {'type': 'EXACT_PRODUCT_NAME_GROUP', 'productName': '黄冰糖'},
            'dateRange': {'type': 'EXACT', 'date': '2026-09-25'}}
    for message in ['查询黄冰糖2026-09-25的化验记录', '查询黄冰糖2026-09-25的全部版本']:
        with pytest.raises(ValueError, match='exact-name group requires'):
            builder.validate_llm_arguments(tool_name='query_assay_records', state=state,
                                           user_message=message, arguments=args)
    value = builder.validate_llm_arguments(tool_name='query_assay_records', state=state,
        user_message='查询黄冰糖全部规格2026-09-25的化验记录', arguments=args)
    assert value['productScope']['type'] == 'EXACT_PRODUCT_NAME_GROUP'
    state.selected_product = SelectedEntity(None, '黄冰糖全部规格', 'resolver',
                                            {'scopeType': 'EXACT_PRODUCT_NAME_GROUP', 'productName': '黄冰糖'})
    value = builder.validate_llm_arguments(tool_name='query_assay_records', state=state,
        user_message='查看刚才范围2026-09-25的化验记录', arguments=args)
    assert value['productScope']['productName'] == '黄冰糖'


def test_failed_standard_message_means_unsatisfied_requirements_not_missing_configuration():
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient({}))
    message = '未命中任何标准，已按默认标准/优先级选择参考标准'
    status = runtime._adapt_assay_status({'judgeResult': 'FAIL', 'assay': {
        'productName': '黄冰糖', 'sampleDate': '2026-09-25', 'judgeMessage': message,
        'colorValue': 10, 'phValue': 0}}, '黄冰糖')
    detail = runtime._adapt_assay_report_detail({'productLabel': '黄冰糖', 'judgeStatus': 'FAIL',
        'judgeMessage': message, 'standardLabel': '产品标准 v1', 'metrics': []})
    for report in (status, detail):
        assert '未满足适用标准的全部要求' in report.judgeExplanation
        assert '未配置' not in report.judgeExplanation
        assert report.judgeLabel == '不合格'
    assert '未匹配到启用中的化验标准' in runtime._assay_judge_explanation('NO_STANDARD', message)
    assert runtime._assay_judge_explanation('FAIL', '缺少检测值') == '缺少检测值'
