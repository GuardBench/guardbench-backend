import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from performance.runner.acceptance import evaluate
from performance.runner.config import ConfigurationError, load_profile
from performance.runner.dataset import load_seed_payload
from performance.runner.cli import K6_THRESHOLD_FAILURE_EXIT_CODE, RunnerError, reset_database, run_k6
from performance.runner.safety import migration_jdbc_url, validate_reset_safety
from performance.runner.storage import RESULT_FILENAMES, ResultUploadError, upload_result_directory


ROOT = Path(__file__).resolve().parents[1]


def acceptance_profile() -> dict:
    return {
        "acceptance": {
            "api": {"create_latency_ms": {"p50": 1000, "p95": 1000, "p99": 1000}, "error_rate": 0},
            "completion": {"max_seconds": 10, "failure_rate": 0, "queue_drain_seconds": 10, "dlq_messages": 0},
        }
    }


def passing_summary() -> dict:
    return {"metrics": {
        "test_run_create_latency": {"values": {"p(50)": 1, "p(95)": 1, "p(99)": 1}},
        "test_run_create_errors": {"values": {"rate": 0}},
        "test_run_completion_failures": {"values": {"rate": 0}},
        "test_run_completion_duration": {"values": {"p(95)": 1}},
    }}


def k6_profile() -> dict:
    profile = acceptance_profile()
    profile.update({
        "workload": {"concurrent_test_runs": 1, "ramp_up_seconds": 1, "duration_seconds": 1,
                     "completion_timeout_seconds": 1, "polling_interval_seconds": 1},
        "target": {"identifier": "https://target.example.test/v1/chat/completions", "model": "test-model",
                   "evaluation_profile": {"checks": ["correctness"], "strictness": "STRICT"}},
    })
    return profile


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
        result = evaluate(acceptance_profile(), passing_summary(), {"passed": False, "duration_seconds": 11}, [],
                          {"status": "COLLECTED", "metrics": [{"id": "ecs_cpu"}]})

        self.assertEqual("FAIL", result["status"])

    def test_acceptance_fails_when_cloudwatch_has_no_datapoints(self):
        aws_metrics = {"status": "COLLECTED", "metrics": [
            {"id": "ecs_cpu_utilization", "values": []},
            {"id": "sqs_resolve_visible", "values": []},
            {"id": "rds_cpu_utilization", "values": []},
        ]}

        result = evaluate(acceptance_profile(), passing_summary(), {"passed": True, "duration_seconds": 1}, [], aws_metrics)

        self.assertEqual("FAIL", result["status"])

    def test_migration_jdbc_url_is_derived_from_reset_target(self):
        jdbc_url = migration_jdbc_url({
            "PERFORMANCE_DATABASE_URL": "postgresql://user:secret@perf-db:5432/guardbench_perf?sslmode=require",
        })

        self.assertEqual("jdbc:postgresql://perf-db:5432/guardbench_perf?sslmode=require", jdbc_url)

    def test_migration_jdbc_url_rejects_different_target(self):
        environment = {
            "PERFORMANCE_DATABASE_URL": "postgresql://user:secret@perf-db:5432/guardbench_perf",
            "PERFORMANCE_DATABASE_JDBC_URL": "jdbc:postgresql://dev-db:5432/guardbench",
        }

        with self.assertRaises(ConfigurationError):
            migration_jdbc_url(environment)

    def test_acceptance_fails_when_k6_thresholds_fail(self):
        aws_metrics = {"status": "COLLECTED", "metrics": [
            {"id": "ecs_cpu_utilization", "values": [1]},
            {"id": "sqs_resolve_visible", "values": [1]},
            {"id": "rds_cpu_utilization", "values": [1]},
        ]}

        result = evaluate(acceptance_profile(), passing_summary(), {"passed": True, "duration_seconds": 1}, [],
                          aws_metrics, k6_threshold_failed=True)

        self.assertEqual("FAIL", result["status"])
        self.assertIn({"name": "k6.thresholds", "actual": "FAIL", "expected": "PASS", "passed": False},
                      result["checks"])

    def test_k6_threshold_exit_preserves_summary_for_reporting(self):
        with tempfile.TemporaryDirectory() as directory:
            summary_path = Path(directory) / "k6-summary.json"
            summary_path.write_text("{}", encoding="utf-8")
            completed = subprocess.CompletedProcess([], K6_THRESHOLD_FAILURE_EXIT_CODE)

            with patch("performance.runner.cli.subprocess.run", return_value=completed) as run:
                exit_code = run_k6(k6_profile(), "run-id", 1, "http://localhost", summary_path, ROOT.parent)

        self.assertEqual(K6_THRESHOLD_FAILURE_EXIT_CODE, exit_code)
        self.assertFalse(run.call_args.kwargs["check"])

    def test_k6_unexpected_exit_is_runner_error(self):
        with tempfile.TemporaryDirectory() as directory:
            summary_path = Path(directory) / "k6-summary.json"
            completed = subprocess.CompletedProcess([], 1)

            with patch("performance.runner.cli.subprocess.run", return_value=completed):
                with self.assertRaises(RunnerError):
                    run_k6(k6_profile(), "run-id", 1, "http://localhost", summary_path, ROOT.parent)

    def test_completed_result_files_are_uploaded_to_the_run_prefix(self):
        class FakeS3:
            def __init__(self):
                self.calls = []

            def upload_file(self, filename, bucket, key):
                self.calls.append((Path(filename).name, bucket, key))

        with tempfile.TemporaryDirectory() as directory:
            result_dir = Path(directory)
            for filename in RESULT_FILENAMES:
                (result_dir / filename).write_text("{}", encoding="utf-8")
            client = FakeS3()

            upload_result_directory(result_dir, "performance-results", "small-123", s3_client=client)

        self.assertEqual(len(RESULT_FILENAMES), len(client.calls))
        self.assertEqual(
            ("result.json", "performance-results", "performance/results/small-123/result.json"),
            client.calls[-2],
        )

    def test_result_upload_rejects_incomplete_runs(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(ResultUploadError):
                upload_result_directory(Path(directory), "performance-results", "small-123", s3_client=object())

    def test_verify_runtime_checks_packaged_contract_inputs(self):
        verify_runtime = (ROOT.parent / "bin" / "verify-runtime").read_text(encoding="utf-8")

        self.assertIn("runtime/bin/python3", verify_runtime)
        self.assertIn("runtime/bin/k6", verify_runtime)
        self.assertIn("runtime/bin/psql", verify_runtime)
        self.assertIn("runtime/bin/java", verify_runtime)
        self.assertIn("bin/run-performance", verify_runtime)
        self.assertIn("src/main/resources/db/migration", verify_runtime)
        self.assertIn("performance/profiles/small.yaml", verify_runtime)
        self.assertIn("performance/datasets/baseline-v1.yaml", verify_runtime)

    def test_artifact_launcher_uses_only_bundled_runtime_paths(self):
        launcher = (ROOT.parent / "bin" / "run-performance").read_text(encoding="utf-8")
        gradle = (ROOT.parent / "bin" / "gradle").read_text(encoding="utf-8")

        self.assertIn('PATH="$root/runtime/bin:$PATH"', launcher)
        self.assertIn('PYTHONPATH="$root/runtime/python', launcher)
        self.assertIn('JAVA_HOME="$root/runtime/root/usr/lib/jvm/java-21-amazon-corretto"', launcher)
        self.assertIn('K6_BIN="$root/runtime/bin/k6"', launcher)
        self.assertIn('exec "$root/runtime/bin/python3" -m performance.runner.cli "$@"', launcher)
        self.assertIn('JAVA_HOME="$root/runtime/root/usr/lib/jvm/java-21-amazon-corretto"', gradle)
        self.assertIn('--offline', gradle)


if __name__ == "__main__":
    unittest.main()
