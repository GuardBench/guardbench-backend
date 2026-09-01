import os
import subprocess
import unittest
from pathlib import Path
from unittest.mock import patch

from performance.runner.acceptance import evaluate
from performance.runner.config import ConfigurationError, load_profile
from performance.runner.dataset import load_seed_payload
from performance.runner.cli import reset_database
from performance.runner.safety import validate_reset_safety


ROOT = Path(__file__).resolve().parents[1]


class PerformanceRunnerTest(unittest.TestCase):
    def test_small_profile_is_valid_and_does_not_embed_dataset(self):
        with patch.dict(os.environ, {
            "PERF_TARGET_URL": "https://target.example.test/v1/chat/completions",
            "PERF_TARGET_MODEL": "test-model",
            "PERF_TARGET_REVISION": "test-revision",
        }, clear=True):
            profile = load_profile(ROOT / "profiles/small.yaml")

        self.assertEqual("SMOKE", profile["test"]["type"])
        self.assertNotIn("dataset", profile)

    def test_baseline_dataset_has_78_cases(self):
        payload, count = load_seed_payload(ROOT / "datasets/baseline-v1.yaml")

        self.assertEqual(78, count)
        self.assertEqual(78, len(payload["testCases"]))

    def test_missing_profile_target_is_rejected(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(ConfigurationError):
                load_profile(ROOT / "profiles/small.yaml")

    def test_reset_requires_all_safety_guards(self):
        with self.assertRaises(ConfigurationError):
            validate_reset_safety({})
        environment = {
            "PERFORMANCE_ENVIRONMENT": "performance",
            "PERFORMANCE_DB_NAME": "guardbench_perf",
            "PERFORMANCE_RESET_CONFIRM": "RESET_GUARDBENCH_PERF",
            "PERFORMANCE_DATABASE_URL": "postgresql://guardbench:secret@localhost:5432/guardbench_perf",
        }
        validate_reset_safety(environment)

    def test_reset_rejects_a_different_database_in_the_connection_url(self):
        environment = {
            "PERFORMANCE_ENVIRONMENT": "performance",
            "PERFORMANCE_DB_NAME": "guardbench_perf",
            "PERFORMANCE_RESET_CONFIRM": "RESET_GUARDBENCH_PERF",
        }

        with self.assertRaises(ConfigurationError):
            validate_reset_safety(environment, "postgresql://guardbench:secret@localhost:5432/dev")

    def test_reset_checks_current_database_before_dropping_schema(self):
        environment = {
            "PERFORMANCE_ENVIRONMENT": "performance",
            "PERFORMANCE_DB_NAME": "guardbench_perf",
            "PERFORMANCE_RESET_CONFIRM": "RESET_GUARDBENCH_PERF",
            "PERFORMANCE_DATABASE_URL": "postgresql://guardbench:secret@localhost:5432/guardbench_perf",
        }
        completed = subprocess.CompletedProcess([], 0, stdout="guardbench_perf\nDO\n", stderr="")

        with patch("performance.runner.cli.subprocess.run", return_value=completed) as run:
            reset_database(environment)

        sql = run.call_args.args[0][-1]
        self.assertIn("SELECT current_database()", sql)
        self.assertIn("DROP SCHEMA public CASCADE", sql)

    def test_acceptance_fails_when_completion_has_not_drained(self):
        profile = {
            "acceptance": {
                "api": {"create_latency_ms": {"p50": 1000, "p95": 1000, "p99": 1000}, "error_rate": 0},
                "completion": {"max_seconds": 10, "failure_rate": 0, "queue_drain_seconds": 10, "dlq_messages": 0},
            }
        }
        summary = {"metrics": {
            "test_run_create_latency": {"values": {"p(50)": 1, "p(95)": 1, "p(99)": 1}},
            "test_run_create_errors": {"values": {"rate": 0}},
            "test_run_completion_failures": {"values": {"rate": 0}},
            "test_run_completion_duration": {"values": {"p(95)": 1}},
        }}

        result = evaluate(profile, summary, {"passed": False, "duration_seconds": 11}, [],
                          {"status": "COLLECTED", "metrics": [{"id": "ecs_cpu"}]})

        self.assertEqual("FAIL", result["status"])

    def test_acceptance_fails_when_cloudwatch_has_no_datapoints(self):
        profile = {
            "acceptance": {
                "api": {"create_latency_ms": {"p50": 1000, "p95": 1000, "p99": 1000}, "error_rate": 0},
                "completion": {"max_seconds": 10, "failure_rate": 0, "queue_drain_seconds": 10, "dlq_messages": 0},
            }
        }
        summary = {"metrics": {
            "test_run_create_latency": {"values": {"p(50)": 1, "p(95)": 1, "p(99)": 1}},
            "test_run_create_errors": {"values": {"rate": 0}},
            "test_run_completion_failures": {"values": {"rate": 0}},
            "test_run_completion_duration": {"values": {"p(95)": 1}},
        }}
        aws_metrics = {"status": "COLLECTED", "metrics": [
            {"id": "ecs_cpu_utilization", "values": []},
            {"id": "sqs_resolve_visible", "values": []},
            {"id": "rds_cpu_utilization", "values": []},
        ]}

        result = evaluate(profile, summary, {"passed": True, "duration_seconds": 1}, [], aws_metrics)

        self.assertEqual("FAIL", result["status"])


if __name__ == "__main__":
    unittest.main()
