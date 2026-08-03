from __future__ import annotations

from datetime import datetime, timezone

import pytest

from app.business_time import BusinessClock
from app.graph.state import InMemoryCheckpointer, SelectedEntity
from app.runtime import WarehouseAgentRuntime
from app.tool_arguments import ToolArgumentBuilder
from app.tools.client import MockToolClient


def fixed_clock() -> BusinessClock:
    # 2026-07-16 16:30 UTC is already 2026-07-17 in Beijing.
    return BusinessClock(lambda: datetime(2026, 7, 16, 16, 30, tzinfo=timezone.utc))


@pytest.mark.parametrize(
    ("message", "expected_start", "expected_end"),
    [
        ("查询今天的化验", "2026-07-17", "2026-07-17"),
        ("查询昨天的化验", "2026-07-16", "2026-07-16"),
        ("查询本周的化验", "2026-07-13", "2026-07-17"),
        ("查询上周的化验", "2026-07-06", "2026-07-12"),
        ("查询这个月的化验", "2026-07-01", "2026-07-17"),
        ("查询上个月的化验", "2026-06-01", "2026-06-30"),
        ("查询本季度的化验", "2026-07-01", "2026-07-17"),
        ("查询上季度的化验", "2026-04-01", "2026-06-30"),
        ("查询最近三十天的化验", "2026-06-18", "2026-07-17"),
        ("查询今年的化验", "2026-01-01", "2026-07-17"),
        ("查询黄冰糖（袋）7月17日的化验情况", "2026-07-17", "2026-07-17"),
        ("查询黄冰糖（袋）2026年7月16号的化验情况", "2026-07-16", "2026-07-16"),
        ("查询黄冰糖（袋）2026/07/15的化验情况", "2026-07-15", "2026-07-15"),
        ("查询2026年7月14日至7月20日的库存趋势", "2026-07-14", "2026-07-20"),
        ("查询7月14日到20日的库存趋势", "2026-07-14", "2026-07-20"),
        ("查询2026-07-14至2026-07-20的库存趋势", "2026-07-14", "2026-07-20"),
    ],
)
def test_resolves_relative_dates_in_beijing_time(
    message: str,
    expected_start: str,
    expected_end: str,
) -> None:
    resolved = fixed_clock().resolve(message)

    assert resolved is not None
    assert resolved.start.isoformat() == expected_start
    assert resolved.end.isoformat() == expected_end


def test_explicit_date_range_is_not_collapsed_to_its_first_day() -> None:
    resolved = fixed_clock().resolve("查询2026年7月14日至7月20日黄冰糖（袋）的库存变化趋势")

    assert resolved is not None
    assert resolved.to_tool_date_range() == {
        "type": "RANGE",
        "from": "2026-07-14",
        "to": "2026-07-20",
    }


def test_business_time_context_is_explicit_and_server_trusted() -> None:
    context = fixed_clock().context()

    assert context["trustedSource"] == "SERVER_RUNTIME"
    assert context["timezone"] == "Asia/Shanghai"
    assert context["currentDate"] == "2026-07-17"
    assert context["currentDateTime"].endswith("+08:00")
    assert context["lastWeek"] == {
        "label": "上周",
        "from": "2026-07-06",
        "to": "2026-07-12",
    }


def test_runtime_injects_business_time_into_every_llm_selected_context() -> None:
    runtime = WarehouseAgentRuntime(
        MockToolClient({}),
        business_clock=fixed_clock(),
    )
    state = InMemoryCheckpointer().get("agt_llm_time_context")

    context = runtime._llm_selected_context(state)

    assert context["BUSINESS_TIME"]["currentDate"] == "2026-07-17"
    assert context["BUSINESS_TIME"]["timezone"] == "Asia/Shanghai"


def test_runtime_overrides_model_stale_today_before_assay_tool_call() -> None:
    state = InMemoryCheckpointer().get("agt_time")
    state.selected_product = SelectedEntity(
        84,
        "黄冰糖（袋）",
        "tool_result",
        {"productName": "黄冰糖（袋）", "scopeType": "SINGLE_PRODUCT"},
        entity_type="PRODUCT",
        entity_ref="CURRENT_PRODUCT",
        canonical_name="黄冰糖（袋）",
    )
    builder = ToolArgumentBuilder(business_clock=fixed_clock())

    validated = builder.validate_llm_arguments(
        tool_name="get_assay_status",
        arguments={
            "productRef": "CURRENT_PRODUCT",
            "productionDate": "2025-04-09",
        },
        state=state,
        user_message="查询黄冰糖（袋）今天的化验情况",
    )

    assert validated == {"productId": 84, "productionDate": "2026-07-17"}


def test_runtime_applies_current_year_to_chinese_month_day_before_assay_tool_call() -> None:
    state = InMemoryCheckpointer().get("agt_month_day")
    state.selected_product = SelectedEntity(
        84,
        "黄冰糖（袋）",
        "tool_result",
        {"productName": "黄冰糖（袋）", "scopeType": "SINGLE_PRODUCT"},
        entity_type="PRODUCT",
        entity_ref="CURRENT_PRODUCT",
        canonical_name="黄冰糖（袋）",
    )
    builder = ToolArgumentBuilder(business_clock=fixed_clock())

    validated = builder.validate_llm_arguments(
        tool_name="get_assay_status",
        arguments={
            "productRef": "CURRENT_PRODUCT",
            "productionDate": "2025-07-17",
        },
        state=state,
        user_message="查询黄冰糖（袋）7月17日的化验情况",
    )

    assert validated == {"productId": 84, "productionDate": "2026-07-17"}


def test_runtime_overrides_model_stale_range_before_history_tool_call() -> None:
    state = InMemoryCheckpointer().get("agt_time_range")
    builder = ToolArgumentBuilder(business_clock=fixed_clock())

    validated = builder.validate_llm_arguments(
        tool_name="query_assay_records",
        arguments={
            "productScope": {"type": "ALL"},
            "dateRange": {"type": "EXACT", "date": "2025-04-09"},
            "page": 1,
            "size": 20,
        },
        state=state,
        user_message="查询今天有哪些化验记录",
    )

    assert validated["dateRange"] == {"type": "EXACT", "date": "2026-07-17"}


def test_date_range_request_rejects_single_date_assay_tool() -> None:
    state = InMemoryCheckpointer().get("agt_time_single_date")
    state.selected_product = SelectedEntity(
        84,
        "黄冰糖（袋）",
        "tool_result",
        {"productName": "黄冰糖（袋）", "scopeType": "SINGLE_PRODUCT"},
        entity_type="PRODUCT",
        entity_ref="CURRENT_PRODUCT",
        canonical_name="黄冰糖（袋）",
    )
    builder = ToolArgumentBuilder(business_clock=fixed_clock())

    with pytest.raises(ValueError, match="accepts one productionDate"):
        builder.validate_llm_arguments(
            tool_name="get_assay_status",
            arguments={
                "productRef": "CURRENT_PRODUCT",
                "productionDate": "2025-04-09",
            },
            state=state,
            user_message="查询黄冰糖（袋）这个月的化验情况",
        )


def test_single_product_exact_date_rejects_history_tool() -> None:
    state = InMemoryCheckpointer().get("agt_time_exact_report")
    state.selected_product = SelectedEntity(
        84,
        "黄冰糖（袋）",
        "tool_result",
        {"productName": "黄冰糖（袋）", "scopeType": "SINGLE_PRODUCT"},
        entity_type="PRODUCT",
        entity_ref="CURRENT_PRODUCT",
        canonical_name="黄冰糖（袋）",
    )
    builder = ToolArgumentBuilder(business_clock=fixed_clock())

    with pytest.raises(ValueError, match="must use get_assay_status"):
        builder.validate_llm_arguments(
            tool_name="query_assay_records",
            arguments={
                "productScope": {
                    "type": "SINGLE_PRODUCT",
                    "productRef": "CURRENT_PRODUCT",
                },
                "dateRange": {"type": "EXACT", "date": "2025-04-09"},
                "page": 1,
                "size": 20,
            },
            state=state,
            user_message="查询黄冰糖（袋）今天的化验情况",
        )


def test_explicit_request_for_all_assay_versions_can_use_history_tool() -> None:
    state = InMemoryCheckpointer().get("agt_time_version_list")
    state.selected_product = SelectedEntity(
        84,
        "黄冰糖（袋）",
        "tool_result",
        {"productName": "黄冰糖（袋）", "scopeType": "SINGLE_PRODUCT"},
        entity_type="PRODUCT",
        entity_ref="CURRENT_PRODUCT",
        canonical_name="黄冰糖（袋）",
    )
    builder = ToolArgumentBuilder(business_clock=fixed_clock())

    validated = builder.validate_llm_arguments(
        tool_name="query_assay_records",
        arguments={
            "productScope": {
                "type": "SINGLE_PRODUCT",
                "productRef": "CURRENT_PRODUCT",
            },
            "dateRange": {"type": "EXACT", "date": "2026-07-17"},
            "page": 1,
            "size": 20,
        },
        state=state,
        user_message="查询黄冰糖（袋）今天的全部版本",
    )

    assert validated["dateRange"] == {"type": "EXACT", "date": "2026-07-17"}


def test_relative_month_is_applied_to_log_time_window() -> None:
    state = InMemoryCheckpointer().get("agt_log_time")
    builder = ToolArgumentBuilder(business_clock=fixed_clock())

    validated = builder.validate_llm_arguments(
        tool_name="search_operation_logs",
        arguments={"page": 1, "size": 20},
        state=state,
        user_message="查询本月的操作日志",
    )

    assert validated["startTime"] == "2026-07-01T00:00:00+08:00"
    assert validated["endTime"] == "2026-07-17T23:59:59+08:00"
