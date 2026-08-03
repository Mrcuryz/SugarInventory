from app.observability import (
    MetricsRegistry,
    record_turn_tool_duration,
    reset_turn_diagnostics,
    take_turn_tool_durations,
)


def test_metrics_snapshot_exposes_nearest_rank_p50_and_p95() -> None:
    metrics = MetricsRegistry()
    for value in range(1, 21):
        metrics.observe("agent_turn_duration", float(value), mode="llm", operation="chat")

    histogram = metrics.snapshot()["histograms"][
        'agent_turn_duration{mode="llm",operation="chat"}'
    ]

    assert histogram == {
        "count": 20,
        "sum": 210.0,
        "max": 20.0,
        "p50": 10.0,
        "p95": 19.0,
    }


def test_empty_registered_histogram_includes_percentile_fields() -> None:
    histogram = MetricsRegistry().snapshot()["histograms"]["stream_first_progress_duration"]

    assert histogram == {"count": 0, "sum": 0, "max": 0, "p50": 0, "p95": 0}


def test_turn_tool_diagnostics_are_request_local_and_consumed_once() -> None:
    reset_turn_diagnostics()
    record_turn_tool_duration(0.012)
    record_turn_tool_duration(-1)

    assert take_turn_tool_durations() == [0.012, 0.0]
    assert take_turn_tool_durations() == []
