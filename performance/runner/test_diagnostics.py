import os
import unittest
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

from performance.runner.acceptance import evaluate
from performance.runner.aws import CloudWatchMetricCollector, QueueInspector
from performance.runner.cli import _collect_run_diagnostics, _required_revision, _threshold_inputs
from performance.runner.config import ConfigurationError

ROOT = Path(__file__).resolve().parents[1]


def profile() -> dict:
    return {
        "workload": {
            "concurrent_test_runs": 1,
            "ramp_up_seconds": 0,
            "duration_seconds": 10,
            "max_iterations_per_vu": 1,
            "completion_timeout_seconds": 300,
            "polling_interval_seconds": 2,
        },
        "target": {
            "identifier": "https://target.example.test/v1/chat/completions",
            "model": "test-model",
            "revision": "target-revision",
        },
        "acceptance": {
            "api": {
                "create_latency_ms": {"p50": 1000, "p95": 2000, "p99": 3000},
                "error_rate": 0,
            },
            "completion": {
                "max_seconds": 300,
                "failure_rate": 0,
                "queue_drain_seconds": 120,
                "dlq_messages": 0,
            },
        },
    }


def aws_metrics() -> dict:
    return {
        "status": "COLLECTED",
        "metrics": [
            {"id": "ecs_cpu_utilization", "values": [1]},
            {"id": "sqs_workitems_visible", "values": [0]},
            {"id": "rds_cpu_utilization", "values": [1]},
            {"id": "sagemaker_invocations", "values": [78]},
        ],
    }


class PerformanceDiagnosticsTest(unittest.TestCase):
    def test_acceptance_reads_k6_055_summary_export_shape(self):
        summary = {
            "metrics": {
                "test_run_create_latency": {
                    "avg": 1979.597112,
                    "med": 1979.597112,
                    "p(50)": 1979.597112,
                    "p(90)": 1979.597112,
                    "p(95)": 1979.597112,
                    "p(99)": 1979.597112,
                },
                "test_run_create_errors": {"rate": 0},
                "test_run_completion_failures": {"rate": 0},
                "test_run_completion_duration": {"p(95)": 212.967},
            }
        }

        result = evaluate(
            profile(), summary,
            {"passed": True, "duration_seconds": 0.058},
            [], aws_metrics(),
        )
        by_name = {check["name"]: check for check in result["checks"]}

        self.assertEqual(1979.597112, by_name["api.create_latency.p(95)"]["actual"])
        self.assertTrue(by_name["api.create_latency.p(95)"]["passed"])
        self.assertEqual(0.0, by_name["api.create_error_rate"]["actual"])
        self.assertEqual(0.0, by_name["completion.failure_rate"]["actual"])
        self.assertEqual(212.967, by_name["completion.duration.p95"]["actual"])
        self.assertTrue(by_name["completion.duration.p95"]["passed"])

    def test_acceptance_keeps_nested_values_fixture_compatibility(self):
        summary = {
            "metrics": {
                "test_run_create_latency": {"values": {"p(50)": 1, "p(95)": 1, "p(99)": 1}},
                "test_run_create_errors": {"values": {"rate": 0}},
                "test_run_completion_failures": {"values": {"rate": 0}},
                "test_run_completion_duration": {"values": {"p(95)": 1}},
            }
        }

        result = evaluate(
            profile(), summary,
            {"passed": True, "duration_seconds": 1},
            [], aws_metrics(),
        )

        self.assertEqual("PASS", result["status"])

    def test_k6_exports_all_acceptance_percentiles(self):
        script = (ROOT / "k6" / "test-run.js").read_text(encoding="utf-8")
        self.assertIn("summaryTrendStats", script)
        self.assertIn("'p(50)'", script)
        self.assertIn("'p(95)'", script)
        self.assertIn("'p(99)'", script)

    def test_required_revisions_reject_unknown(self):
        with patch.dict(os.environ, {"APP_REVISION": "unknown"}, clear=True):
            with self.assertRaises(ConfigurationError):
                _required_revision("APP_REVISION")

    def test_threshold_inputs_are_preserved_from_profile(self):
        inputs = _threshold_inputs(profile(), "run-1", 1, "https://api.example.test")
        self.assertEqual("1000", inputs["PERF_API_P50_MS"])
        self.assertEqual("2000", inputs["PERF_API_P95_MS"])
        self.assertEqual("3000", inputs["PERF_API_P99_MS"])
        self.assertEqual("300", inputs["PERF_COMPLETION_MAX_SECONDS"])

    def test_queue_snapshot_does_not_fabricate_oldest_age(self):
        class FakeSqs:
            def get_queue_attributes(self, **kwargs):
                self.request = kwargs
                return {"Attributes": {
                    "ApproximateNumberOfMessages": "0",
                    "ApproximateNumberOfMessagesNotVisible": "0",
                    "ApproximateNumberOfMessagesDelayed": "0",
                }}

        inspector = QueueInspector.__new__(QueueInspector)
        inspector.client = FakeSqs()
        snapshot = inspector.snapshot(["https://sqs.example.test/queue"])

        self.assertIsNone(snapshot[0]["oldestAgeSeconds"])
        self.assertNotIn("ApproximateAgeOfOldestMessage", inspector.client.request["AttributeNames"])

    def test_metric_configuration_rejects_unresolved_dimensions(self):
        collector = CloudWatchMetricCollector.__new__(CloudWatchMetricCollector)
        collector.config = {
            "metrics": [{
                "id": "sqs_workitems_visible",
                "dimensions": {"QueueName": "${PERF_SOURCE_QUEUE_WORK_ITEMS_NAME}"},
            }]
        }

        with self.assertRaisesRegex(ConfigurationError, "sqs_workitems_visible"):
            collector.validate_configuration()

    def test_failed_run_diagnostics_preserve_ids_and_error_summary(self):
        class FakeApi:
            def list_runs(self, **kwargs):
                return [
                    {"id": 41, "testSuiteId": 7},
                    {"id": 42, "testSuiteId": 7},
                    {"id": 99, "testSuiteId": 8},
                ]

            def get_run(self, run_id):
                if run_id == 41:
                    return {"id": 41, "status": "FINISHED", "executionOutcome": "COMPLETED"}
                if run_id == 42:
                    return {"id": 42, "status": "FINISHED", "executionOutcome": "ERROR"}
                raise AssertionError("other suite must not be queried")

            def list_run_results(self, run_id):
                if run_id == 42:
                    return [
                        {"error": {"stage": "EVALUATOR", "code": "PROVIDER_TIMEOUT", "message": "safe"}},
                        {"error": {"stage": "EVALUATOR", "code": "PROVIDER_TIMEOUT", "message": "safe"}},
                    ]
                return []

        now = datetime.now(timezone.utc)
        diagnostics = _collect_run_diagnostics(FakeApi(), 7, now, now)

        self.assertEqual(2, diagnostics["test_run_count"])
        self.assertEqual([42], diagnostics["failed_test_run_ids"])
        self.assertEqual({"EVALUATOR:PROVIDER_TIMEOUT": 2}, diagnostics["error_summary"])
        self.assertEqual("ERROR", diagnostics["failed_runs"][0]["executionOutcome"])


if __name__ == "__main__":
    unittest.main()
