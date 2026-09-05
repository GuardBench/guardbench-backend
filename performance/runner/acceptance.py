"""Evaluate k6 and system-level criteria without changing the source profile."""

from __future__ import annotations

from typing import Any


def _metric(summary: dict[str, Any], name: str) -> dict[str, Any]:
    """Return metric values from both k6 summary-export shapes.

    k6 0.55 writes metric aggregates directly below ``metrics[name]`` while
    older fixtures used a nested ``values`` object. Accept both so historical
    artifacts remain readable without masking missing metrics.
    """
    metric = summary.get("metrics", {}).get(name, {})
    if not isinstance(metric, dict):
        return {}
    values = metric.get("values")
    return values if isinstance(values, dict) else metric


def _value(values: dict[str, Any], key: str) -> float | None:
    value = values.get(key)
    return float(value) if isinstance(value, (int, float)) else None


def evaluate(profile: dict[str, Any], summary: dict[str, Any],
             drain: dict[str, Any], final_queues: list[dict[str, Any]],
             aws_metrics: dict[str, Any], k6_threshold_failed: bool = False) -> dict[str, Any]:
    api = profile["acceptance"]["api"]
    completion = profile["acceptance"]["completion"]
    checks: list[dict[str, Any]] = []

    latency = _metric(summary, "test_run_create_latency")
    for percentile in ("p(50)", "p(95)", "p(99)"):
        actual = _value(latency, percentile)
        expected = float(api["create_latency_ms"]["p" + percentile[2:-1]])
        checks.append({"name": f"api.create_latency.{percentile}", "actual": actual, "expected": f"< {expected} ms",
                       "passed": actual is not None and actual < expected})

    create_errors = _value(_metric(summary, "test_run_create_errors"), "rate")
    checks.append({"name": "api.create_error_rate", "actual": create_errors,
                   "expected": f"<= {api['error_rate']}",
                   "passed": create_errors is not None and create_errors <= api["error_rate"]})
    completion_failures = _value(_metric(summary, "test_run_completion_failures"), "rate")
    checks.append({"name": "completion.failure_rate", "actual": completion_failures,
                   "expected": f"<= {completion['failure_rate']}",
                   "passed": completion_failures is not None and completion_failures <= completion["failure_rate"]})
    completion_duration = _value(_metric(summary, "test_run_completion_duration"), "p(95)")
    checks.append({"name": "completion.duration.p95", "actual": completion_duration,
                   "expected": f"<= {completion['max_seconds']} s",
                   "passed": completion_duration is not None and completion_duration <= completion["max_seconds"]})
    checks.append({"name": "completion.queue_drain", "actual": drain.get("duration_seconds"),
                   "expected": f"<= {completion['queue_drain_seconds']} s",
                   "passed": drain.get("passed") is True and drain.get("duration_seconds", float("inf")) <= completion["queue_drain_seconds"]})

    dlq_messages = sum(queue["visible"] for queue in final_queues)
    checks.append({"name": "completion.dlq_messages", "actual": dlq_messages,
                   "expected": f"<= {completion['dlq_messages']}",
                   "passed": dlq_messages <= completion["dlq_messages"]})
    collected_metrics = aws_metrics.get("metrics", [])
    has_datapoint = lambda prefix: any(
        metric.get("id", "").startswith(prefix) and bool(metric.get("values"))
        for metric in collected_metrics
    )
    checks.append({"name": "aws.metrics_collected", "actual": aws_metrics.get("status"),
                   "expected": "COLLECTED with ECS/SQS/RDS/SageMaker datapoints",
                   "passed": aws_metrics.get("status") == "COLLECTED"
                   and has_datapoint("ecs_") and has_datapoint("sqs_") and has_datapoint("rds_")
                   and has_datapoint("sagemaker_")})
    checks.append({"name": "k6.thresholds", "actual": "FAIL" if k6_threshold_failed else "PASS",
                   "expected": "PASS", "passed": not k6_threshold_failed})
    return {"status": "PASS" if all(check["passed"] for check in checks) else "FAIL", "checks": checks}
