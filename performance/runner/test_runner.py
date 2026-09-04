import os
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from performance.runner.acceptance import evaluate
from performance.runner.config import ConfigurationError, load_profile, load_yaml
from performance.runner.dataset import load_seed_payload
from performance.runner.aws import InfrastructureCapacityCollector
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
        "target": {"identifier": "https://target.example.test/v1/chat/completions", "model": "test-model"},
    })
    return profile


class PerformanceRunnerTest(unittest.TestCase):
    def test_infrastructure_capacity_snapshot_collects_configured_capacity(self):
        class FakeEcs:
            def describe_services(self, **kwargs):
                self.service_request = kwargs
                return {"services": [{
                    "taskDefinition": "arn:aws:ecs:region:account:task-definition/app:7",
                    "desiredCount": 2,
                    "runningCount": 2,
                }]}

            def describe_task_definition(self, **kwargs):
                self.task_request = kwargs
                return {"taskDefinition": {"cpu": "512", "memory": "1024"}}

        class FakeRds:
            def describe_db_instances(self, **kwargs):
                self.request = kwargs
                return {"DBInstances": [{
                    "DBInstanceIdentifier": "guardbench-dev",
                    "DBInstanceClass": "db.t4g.medium",
                }]}

        class FakeSageMaker:
            def describe_endpoint(self, **kwargs):
                self.request = kwargs
                return {"ProductionVariants": [{
                    "VariantName": "AllTraffic",
                    "InstanceType": "ml.g4dn.xlarge",
                    "DesiredInstanceCount": 1,
                    "CurrentInstanceCount": 1,
                }]}

        with patch.dict(os.environ, {
            "PERF_ECS_CLUSTER": "guardbench-dev",
            "PERF_ECS_SERVICE": "app",
            "PERF_RDS_INSTANCE_ID": "guardbench-dev",
            "PERF_SAGEMAKER_ENDPOINT_NAME": "classifier",
            "PERF_SAGEMAKER_VARIANT_NAME": "AllTraffic",
        }, clear=True):
            snapshot = InfrastructureCapacityCollector(
                ecs_client=FakeEcs(), rds_client=FakeRds(), sagemaker_client=FakeSageMaker()
            ).collect()

        self.assertEqual("guardbench-dev", snapshot["ecs"]["cluster_identifier"])
        self.assertEqual(2, snapshot["ecs"]["desired_count"])
        self.assertEqual("512", snapshot["ecs"]["task_cpu"])
        self.assertEqual("db.t4g.medium", snapshot["rds"]["db_instance_class"])
        self.assertEqual("ml.g4dn.xlarge", snapshot["sagemaker"]["instance_type"])
        self.assertEqual(1, snapshot["sagemaker"]["current_instance_count"])
        self.assertIn("captured_at", snapshot)

    def test_infrastructure_capacity_snapshot_requires_all_resource_identifiers(self):
        with patch.dict(os.environ, {}, clear=True):
            with self.assertRaises(ConfigurationError):
                InfrastructureCapacityCollector(
                    ecs_client=object(), rds_client=object(), sagemaker_client=object()
                ).collect()

    def test_infrastructure_capacity_snapshot_fails_when_resource_lookup_fails(self):
        class FailingEcs:
            def describe_services(self, **kwargs):
                raise RuntimeError("access denied")

        with patch.dict(os.environ, {
            "PERF_ECS_CLUSTER": "cluster",
            "PERF_ECS_SERVICE": "service",
            "PERF_RDS_INSTANCE_ID": "db",
            "PERF_SAGEMAKER_ENDPOINT_NAME": "endpoint",
            "PERF_SAGEMAKER_VARIANT_NAME": "variant",
        }, clear=True):
            with self.assertRaisesRegex(ConfigurationError, "Infrastructure Capacity 조회에 실패했습니다"):
                InfrastructureCapacityCollector(
                    ecs_client=FailingEcs(), rds_client=object(), sagemaker_client=object()
                ).collect()

    def test_small_profile_is_valid_and_does_not_embed_dataset(self):
        with patch.dict(os.environ, {
            "PERF_TARGET_URL": "https://target.example.test/v1/chat/completions",
            "PERF_TARGET_MODEL": "test-model",
            "PERF_TARGET_REVISION": "test-revision",
        }, clear=True):
            profile = load_profile(ROOT / "profiles/small.yaml")

        self.assertEqual("SMOKE", profile["test"]["type"])
        self.assertNotIn("dataset", profile)
        self.assertNotIn("evaluation_profile", profile["target"])

    def test_classifier_metrics_are_declared_with_endpoint_dimensions(self):
        with patch.dict(os.environ, {
            "PERF_SAGEMAKER_ENDPOINT_NAME": "classifier-endpoint",
            "PERF_SAGEMAKER_VARIANT_NAME": "AllTraffic",
        }, clear=False):
            metrics = load_yaml(ROOT / "metrics/aws.yaml")["metrics"]

        by_id = {metric["id"]: metric for metric in metrics}
        expected = {
            "sagemaker_invocations",
            "sagemaker_model_latency",
            "sagemaker_overhead_latency",
            "sagemaker_invocation_4xx_errors",
            "sagemaker_invocation_5xx_errors",
        }
        self.assertTrue(expected.issubset(by_id))
        for metric_id in expected:
            self.assertEqual("AWS/SageMaker", by_id[metric_id]["namespace"])
            self.assertEqual("classifier-endpoint", by_id[metric_id]["dimensions"]["EndpointName"])
            self.assertEqual("AllTraffic", by_id[metric_id]["dimensions"]["VariantName"])

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
            {"id": "sagemaker_invocations", "values": []},
        ]}

        result = evaluate(acceptance_profile(), passing_summary(), {"passed": True, "duration_seconds": 1}, [], aws_metrics)

        self.assertEqual("FAIL", result["status"])

    def test_acceptance_requires_classifier_metrics(self):
        aws_metrics = {"status": "COLLECTED", "metrics": [
            {"id": "ecs_cpu_utilization", "values": [1]},
            {"id": "sqs_resolve_visible", "values": [1]},
            {"id": "rds_cpu_utilization", "values": [1]},
        ]}

        result = evaluate(acceptance_profile(), passing_summary(), {"passed": True, "duration_seconds": 1}, [], aws_metrics)

        self.assertEqual("FAIL", result["status"])
        self.assertIn("SageMaker", next(check for check in result["checks"] if check["name"] == "aws.metrics_collected")["expected"])

    def test_acceptance_passes_with_classifier_metrics(self):
        aws_metrics = {"status": "COLLECTED", "metrics": [
            {"id": "ecs_cpu_utilization", "values": [1]},
            {"id": "sqs_resolve_visible", "values": [1]},
            {"id": "rds_cpu_utilization", "values": [1]},
            {"id": "sagemaker_invocations", "values": [78]},
        ]}

        result = evaluate(acceptance_profile(), passing_summary(), {"passed": True, "duration_seconds": 1}, [], aws_metrics)

        self.assertEqual("PASS", result["status"])

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
            {"id": "sagemaker_invocations", "values": [1]},
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

    def test_verify_runtime_checks_container_contract_inputs(self):
        verify_runtime = (ROOT.parent / "bin" / "verify-runtime").read_text(encoding="utf-8")

        self.assertIn("python3.11 --version", verify_runtime)
        self.assertIn("k6 version", verify_runtime)
        self.assertIn("psql --version", verify_runtime)
        self.assertIn("java -version", verify_runtime)
        self.assertIn("javac -version", verify_runtime)
        self.assertIn("bin/run-performance", verify_runtime)
        self.assertIn("src/main/resources/db/migration", verify_runtime)
        self.assertIn("performance/profiles/small.yaml", verify_runtime)
        self.assertIn("performance/datasets/baseline-v1.yaml", verify_runtime)

    def test_container_launcher_uses_image_runtime(self):
        launcher = (ROOT.parent / "bin" / "run-performance").read_text(encoding="utf-8")
        gradle = (ROOT.parent / "bin" / "gradle").read_text(encoding="utf-8")

        self.assertIn('export PYTHONPATH="$root${PYTHONPATH:+:$PYTHONPATH}"', launcher)
        self.assertIn('exec python3.11 -m performance.runner.cli "$@"', launcher)
        self.assertIn('export GRADLE_USER_HOME="${GRADLE_USER_HOME:-$root/.gradle}"', gradle)
        self.assertIn('--offline', gradle)

    def test_dockerfile_is_a_directly_runnable_image(self):
        dockerfile = (ROOT / "Dockerfile").read_text(encoding="utf-8")

        self.assertIn("ARG APP_REVISION=unknown", dockerfile)
        self.assertIn("COPY performance performance", dockerfile)
        self.assertIn("COPY src src", dockerfile)
        self.assertIn("COPY gradle gradle", dockerfile)
        self.assertIn('ENTRYPOINT ["python3.11", "-m", "performance.runner.cli"]', dockerfile)

    def test_image_build_derives_tag_and_revision_from_repository_head(self):
        build_script = (ROOT / "build-runner-image.sh").read_text(encoding="utf-8")
        publish_script = (ROOT.parent / "bin" / "publish-runner-image").read_text(encoding="utf-8")

        self.assertIn('[[ $# -ne 1 || -z "$1" ]]', build_script)
        self.assertIn('repository="$1"', build_script)
        self.assertIn('revision="$(git -C "$root" rev-parse HEAD)"', build_script)
        self.assertIn('image_ref="${repository}:${revision}"', build_script)
        self.assertNotIn('APP_REVISION:-', build_script)
        self.assertIn('"$root/performance/Dockerfile"', build_script)
        self.assertIn('--tag "$image_ref"', build_script)
        self.assertIn('docker push "$1"', publish_script)
        self.assertNotIn("guardbench", build_script)

    def test_shell_script_line_endings_are_pinned_to_lf(self):
        attributes = (ROOT.parent / ".gitattributes").read_text(encoding="utf-8")
        build_script = (ROOT / "build-runner-image.sh").read_bytes()

        self.assertIn("*.sh text eol=lf", attributes)
        self.assertNotIn(b"\r\n", build_script)

    def test_image_build_passes_one_git_sha_to_tag_and_revision(self):
        with tempfile.TemporaryDirectory() as directory:
            fake_bin = Path(directory) / "bin"
            fake_bin.mkdir()
            docker_args = Path(directory) / "docker-args"
            fake_docker = fake_bin / "docker"
            fake_docker.write_text(
                '#!/usr/bin/env bash\nprintf \'%s\\n\' "$@" > "$DOCKER_ARGS_FILE"\n',
                encoding="utf-8",
            )
            fake_docker.chmod(0o755)
            environment = dict(os.environ)
            environment["PATH"] = f"{fake_bin}:{environment['PATH']}"
            environment["DOCKER_ARGS_FILE"] = str(docker_args)
            environment["APP_REVISION"] = "caller-selected-revision"

            completed = subprocess.run(
                [str(ROOT / "build-runner-image.sh"), "registry.example/performance-runner"],
                env=environment,
                capture_output=True,
                text=True,
                check=False,
            )
            revision = subprocess.run(
                ["git", "-C", str(ROOT.parent), "rev-parse", "HEAD"],
                capture_output=True,
                text=True,
                check=True,
            ).stdout.strip()

            self.assertEqual(0, completed.returncode, completed.stderr)
            arguments = docker_args.read_text(encoding="utf-8").splitlines()
            self.assertIn(f"registry.example/performance-runner:{revision}", arguments)
            self.assertIn(f"APP_REVISION={revision}", arguments)
            self.assertNotIn("caller-selected-revision", arguments)

    def test_image_build_rejects_a_repository_with_a_caller_selected_tag(self):
        completed = subprocess.run(
            [str(ROOT / "build-runner-image.sh"), "registry.example/performance-runner:caller-tag"],
            capture_output=True,
            text=True,
            check=False,
        )

        self.assertEqual(2, completed.returncode)
        self.assertIn("must not include a tag", completed.stderr)


if __name__ == "__main__":
    unittest.main()
