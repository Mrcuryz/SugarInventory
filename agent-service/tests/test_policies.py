from __future__ import annotations

from app.policies import FastCompletionPolicy, NextActionPolicy, SafeFallbackPolicy


def test_fast_completion_policy_keeps_combined_queries_on_expert_path() -> None:
    policy = FastCompletionPolicy.default()

    assert policy.allows("get_inventory_overview", "查询黄冰糖库存总览") is True
    assert policy.allows("get_inventory_overview", "查询库存总览并查看化验") is False
    assert policy.allows("get_assay_status", "查看最近30天化验趋势") is False
    assert policy.allows("query_assay_records", "查询化验记录") is True
    assert policy.allows("query_assay_records", "分析最近180天化验趋势") is False
    assert policy.allows("get_assay_report_detail", "展开第一条化验详情") is True


def test_safe_fallback_policy_is_limited_to_core_read_recovery() -> None:
    policy = SafeFallbackPolicy.default()

    assert policy.can_attempt(
        error_code="MODEL_TIMEOUT",
        tool_call_count=1,
        max_tool_calls=3,
        observations=[{"status": "AVAILABLE", "tool": "resolve_products"}],
    ) is True
    assert policy.can_attempt(
        error_code="MODEL_TIMEOUT",
        tool_call_count=1,
        max_tool_calls=3,
        observations=[{"status": "AVAILABLE", "tool": "get_inventory_overview"}],
    ) is False
    assert policy.can_attempt(
        error_code="MODEL_UPSTREAM_ERROR",
        tool_call_count=0,
        max_tool_calls=3,
        observations=[],
    ) is False
    assert policy.allows_plan("call_tool", "get_inventory_distribution") is True
    assert policy.allows_plan("call_tool", "query_assay_records") is False
    assert policy.allows_plan("ask_user", None) is True


def test_next_action_policy_only_offers_supported_contextual_followup() -> None:
    policy = NextActionPolicy()

    assert policy.suggestions("get_inventory_overview", {"isEmpty": False}) == ["查询库存分布"]
    assert policy.suggestions("get_inventory_overview", {"isEmpty": True}) == []
    assert policy.suggestions("get_assay_status", {"isEmpty": False}) == []
