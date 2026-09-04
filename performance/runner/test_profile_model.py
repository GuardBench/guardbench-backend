import os
import unittest
from pathlib import Path
from unittest.mock import patch

from performance.runner.config import load_profile


ROOT = Path(__file__).resolve().parents[1]


class PerformanceProfileModelTest(unittest.TestCase):
    def _load(self, name: str) -> dict:
        with patch.dict(os.environ, {
            "PERF_TARGET_URL": "https://target.example.test/v1/chat/completions",
            "PERF_TARGET_MODEL": "test-model",
            "PERF_TARGET_REVISION": "test-revision",
        }, clear=True):
            return load_profile(ROOT / f"profiles/{name}")

    def test_smoke_is_the_canonical_fixed_profile(self):
        profile = self._load("smoke.yaml")

        self.assertEqual("PERF-SMOKE-01", profile["test"]["id"])
        self.assertEqual("SMOKE", profile["test"]["type"])
        self.assertEqual(1, profile["workload"]["concurrent_test_runs"])
        self.assertEqual(1, profile["workload"]["max_iterations_per_vu"])
        self.assertEqual(300, profile["workload"]["completion_timeout_seconds"])
        self.assertEqual(2, profile["workload"]["polling_interval_seconds"])

    def test_small_alias_does_not_create_a_dataset_size_axis_in_profile(self):
        smoke = self._load("smoke.yaml")
        small = self._load("small.yaml")

        self.assertEqual(smoke, small)
        self.assertNotIn("dataset_size", smoke)
        self.assertNotIn("infrastructure_capacity", smoke)


if __name__ == "__main__":
    unittest.main()
