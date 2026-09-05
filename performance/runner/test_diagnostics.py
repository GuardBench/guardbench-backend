import unittest

from performance.runner.acceptance import evaluate


def profile() -> dict:
    return {
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
        }
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
                    "p(90)": 1979.597112,
                    "p(95)": 1979.597112,
                    "p(99)": 1979.597112,
                    "p(50)": 1979.597112,
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


if __name__ == "__main__":
    unittest.main()
