import os
import unittest
from pathlib import Path
from unittest.mock import patch

from performance.runner.acceptance import evaluate
from performance.runner.config import ConfigurationError, load_profile
from performance.runner.dataset import load_seed_payload
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
        validate_reset_safety({
            "PERFORMANCE_ENVIRONMENT": "performance",
            "PERFORMANCE_DB_NAME": "guardbench_perf",
            "PERFORMANCE_DB_IDENTIFIER": "guardbench-perf",
            "PERFORMANCE_RESET_CONFIRM": "RESET_GUARDBENCH_PERF",
        })

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


if __name__ == "__main__":
    unittest.main()
