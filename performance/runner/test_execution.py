"""Issue #212: execute without DB lifecycle inputs while preserving preflight."""
import json
import os
import tempfile
import unittest
from contextlib import ExitStack
from pathlib import Path
from unittest.mock import patch

from performance.runner import cli
from performance.runner.config import ConfigurationError
from performance.runner.test_runner import passing_summary


class PerformanceExecutionTest(unittest.TestCase):
    def setUp(self):
        self.stack = ExitStack()
        self.addCleanup(self.stack.close)
        directory = self.stack.enter_context(tempfile.TemporaryDirectory())
        self.result_dir = Path(directory) / "result"
        self.args = cli.parser().parse_args(["--result-dir", str(self.result_dir)])
        self.stack.enter_context(patch.dict(os.environ, {
            "APP_REVISION": "app-sha", "INFRA_REVISION": "infra-sha",
            "PERF_BASE_URL": "https://api.example.test",
            "PERF_EXPECTED_WORK_ITEMS_CONCURRENCY": "2",
            "PERF_EXPECTED_ECS_TASK_COUNT": "1",
            "PERF_TARGET_URL": "https://target.example.test/v1/chat/completions",
            "PERF_TARGET_MODEL": "model", "PERF_TARGET_REVISION": "target-sha",
        }, clear=True))
        self.api = self.stack.enter_context(patch.object(cli, "ApiClient")).return_value
        self.api.list_runs.return_value = []
        self.api.create_suite.return_value = 7
        self.stack.enter_context(patch.object(cli, "queue_urls_from_environment", return_value=(["source"], ["dlq"])))
        self.inspector = self.stack.enter_context(patch.object(cli, "QueueInspector")).return_value
        self.inspector.snapshot.return_value = []
        self.inspector.is_empty.return_value = True
        self.metrics = self.stack.enter_context(patch.object(cli, "CloudWatchMetricCollector")).return_value
        self.metrics.collect.return_value = {"status": "COLLECTED", "metrics": [
            {"id": name, "values": [1]} for name in
            ("ecs_cpu", "sqs_visible", "rds_cpu", "sagemaker_invocations")
        ]}
        self.capacity = self.stack.enter_context(patch.object(cli, "InfrastructureCapacityCollector")).return_value
        self.capacity.collect.return_value = {
            "captured_at": "2026-09-05T00:00:00Z",
            "ecs": dict(cluster_identifier="cluster", service_identifier="service", desired_count=1,
                        running_task_count=1, task_cpu="256", task_memory="512",
                        work_items_concurrency=2),
            "rds": dict(db_instance_identifier="perf", db_instance_class="db.t4g.micro"),
            "sagemaker": dict(endpoint_name="classifier", production_variant_name="AllTraffic",
                              instance_type="ml.m5.large", desired_instance_count=1, current_instance_count=1),
        }
        def run_k6(*args):
            args[4].write_text(json.dumps(passing_summary()))
            return 0
        self.k6 = self.stack.enter_context(patch.object(cli, "run_k6", side_effect=run_k6))
        self.subprocess = self.stack.enter_context(patch.object(cli.subprocess, "run", side_effect=AssertionError("unexpected process")))

    def test_execution_without_database_inputs_preserves_results_and_capacity(self):
        self.assertEqual(0, cli.execute(self.args))

        result = json.loads((self.result_dir / "result.json").read_text())
        self.assertEqual(7, result["dataset"]["suite_id"])
        self.assertEqual("perf", result["infrastructure_capacity"]["rds"]["db_instance_identifier"])
        self.assertEqual(2, result["infrastructure_capacity"]["ecs"]["work_items_concurrency"])
        self.assertEqual({"work_items_concurrency": 2, "concurrent_test_runs": 1, "ecs_task_count": 1},
                         result["experiment"])
        self.metrics.validate_configuration.assert_called_once()
        self.api.create_suite.assert_called_once()
        self.k6.assert_called_once()
        self.subprocess.assert_not_called()
        self.assertTrue((self.result_dir / "report.md").is_file())

    def test_active_run_blocks_seed_and_workload(self):
        self.api.list_runs.return_value = [{"id": 9}]
        with self.assertRaisesRegex(cli.RunnerError, "처리 중"):
            cli.execute(self.args)
        self.api.create_suite.assert_not_called()
        self.k6.assert_not_called()

    def test_source_or_dlq_backlog_blocks_seed_and_workload(self):
        for states in ([False], [True, False]):
            with self.subTest(states=states):
                self.args.result_dir = str(self.result_dir) + str(len(states))
                self.inspector.is_empty.side_effect = states
                with self.assertRaisesRegex(cli.RunnerError, "비어 있지"):
                    cli.execute(self.args)
                self.api.create_suite.assert_not_called()
                self.k6.assert_not_called()

    def test_missing_execution_identifiers_block_workload(self):
        for key in ("APP_REVISION", "INFRA_REVISION", "PERF_BASE_URL"):
            with self.subTest(key=key), patch.dict(os.environ, {key: ""}):
                self.args.result_dir = str(self.result_dir) + key
                with self.assertRaises(ConfigurationError):
                    cli.execute(self.args)
                self.k6.assert_not_called()

    def test_metric_configuration_failure_blocks_seed(self):
        self.metrics.validate_configuration.side_effect = ConfigurationError("QueueName")
        with self.assertRaises(ConfigurationError):
            cli.execute(self.args)
        self.api.create_suite.assert_not_called()
        self.k6.assert_not_called()

    def test_capacity_failure_blocks_seed(self):
        self.capacity.collect.side_effect = ConfigurationError("capacity")
        with self.assertRaises(ConfigurationError):
            cli.execute(self.args)
        self.api.create_suite.assert_not_called()
        self.k6.assert_not_called()

    def test_capacity_mismatch_blocks_seed_and_workload(self):
        with patch.dict(os.environ, {"PERF_EXPECTED_WORK_ITEMS_CONCURRENCY": "4"}):
            with self.assertRaisesRegex(cli.RunnerError, "expected worker concurrency = 4"):
                cli.execute(self.args)
        self.api.create_suite.assert_not_called()
        self.k6.assert_not_called()

    def test_task_count_mismatch_blocks_seed_and_workload(self):
        self.capacity.collect.return_value["ecs"]["desired_count"] = 2
        with self.assertRaisesRegex(cli.RunnerError, "actual ECS desired task count = 2"):
            cli.execute(self.args)
        self.api.create_suite.assert_not_called()
        self.k6.assert_not_called()

    def test_running_task_count_mismatch_blocks_seed_and_workload(self):
        self.capacity.collect.return_value["ecs"]["running_task_count"] = 2
        with self.assertRaisesRegex(cli.RunnerError, "actual ECS running task count = 2"):
            cli.execute(self.args)
        self.api.create_suite.assert_not_called()
        self.k6.assert_not_called()
