"""CLI entrypoint for repeatable GuardBench performance experiments."""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import uuid
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .acceptance import evaluate
from .api import ApiClient, ApiError
from .aws import CloudWatchMetricCollector, InfrastructureCapacityCollector, QueueInspector, queue_urls_from_environment
from .config import ConfigurationError, load_dataset, load_profile
from .dataset import load_seed_payload
from .safety import EXPECTED_DATABASE_NAME, migration_jdbc_url, validate_reset_safety
from .storage import ResultUploadError, upload_result_directory


class RunnerError(RuntimeError):
    pass


K6_THRESHOLD_FAILURE_EXIT_CODE = 99


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def _repo_root() -> Path:
    return Path(__file__).resolve().parents[2]


def _required_revision(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value or value.lower() == "unknown":
        raise ConfigurationError(f"실제 성능 실행에는 {name}을(를) 명시해야 합니다.")
    return value


def reset_database(environment: dict[str, str]) -> None:
    database_url = environment.get("PERFORMANCE_DATABASE_URL")
    validate_reset_safety(environment, database_url)
    command = [
        os.environ.get("PSQL_BIN", "psql"), "--no-psqlrc", "--dbname", database_url, "--no-align", "--tuples-only",
        "--set", "ON_ERROR_STOP=1", "--command",
        "SELECT current_database(); "
        "DO $$ BEGIN "
        "IF current_database() <> 'guardbench_perf' THEN "
        "RAISE EXCEPTION 'performance reset target database mismatch'; "
        "END IF; END $$; "
        "DROP SCHEMA public CASCADE; CREATE SCHEMA public;",
    ]
    try:
        result = subprocess.run(command, check=True, capture_output=True, text=True)
    except (OSError, subprocess.CalledProcessError) as exc:
        raise RunnerError("DB reset 대상 확인 또는 실행에 실패했습니다.") from exc
    first_output = next((line.strip() for line in result.stdout.splitlines() if line.strip()), "")
    if first_output != EXPECTED_DATABASE_NAME:
        raise RunnerError("DB reset 대상이 guardbench_perf인지 확인하지 못했습니다.")


def apply_migrations(environment: dict[str, str], migration_dir: Path) -> None:
    database_url = environment.get("PERFORMANCE_DATABASE_URL")
    if not database_url:
        raise ConfigurationError("migration에는 PERFORMANCE_DATABASE_URL이 필요합니다.")
    if not migration_dir.is_dir():
        raise ConfigurationError(f"migration 디렉터리를 찾을 수 없습니다: {migration_dir}")
    jdbc_url = migration_jdbc_url(environment)
    command_text = environment.get("PERFORMANCE_MIGRATION_COMMAND_JSON")
    if command_text:
        try:
            command = json.loads(command_text)
        except json.JSONDecodeError as exc:
            raise ConfigurationError("PERFORMANCE_MIGRATION_COMMAND_JSON은 JSON 배열이어야 합니다.") from exc
        if not isinstance(command, list) or not command or not all(isinstance(item, str) for item in command):
            raise ConfigurationError("PERFORMANCE_MIGRATION_COMMAND_JSON은 문자열 배열이어야 합니다.")
    else:
        gradle_command = environment.get("PERFORMANCE_GRADLE_COMMAND", "./gradlew")
        command = [gradle_command, "bootRun", "--args=--spring.main.web-application-type=none"]
    migration_environment = dict(environment)
    migration_environment.update({
        "SPRING_DATASOURCE_URL": jdbc_url,
        "SPRING_DOCKER_COMPOSE_ENABLED": "false",
        "SQS_ENABLED": "false",
        "WORKER_ENABLED": "false",
    })
    username = environment.get("PERFORMANCE_DB_USERNAME")
    password = environment.get("PERFORMANCE_DB_PASSWORD")
    if username:
        migration_environment["SPRING_DATASOURCE_USERNAME"] = username
    if password:
        migration_environment["SPRING_DATASOURCE_PASSWORD"] = password
    try:
        subprocess.run(command, cwd=_repo_root(), env=migration_environment, check=True)
    except (OSError, subprocess.CalledProcessError) as exc:
        raise RunnerError("Flyway migration command 실행에 실패했습니다.") from exc


def _queue_state(inspector: QueueInspector, source_urls: list[str], dlq_urls: list[str]) -> dict[str, Any]:
    return {"source": inspector.snapshot(source_urls), "dlq": inspector.snapshot(dlq_urls)}


def _assert_preflight(api: ApiClient, inspector: QueueInspector, source_urls: list[str], dlq_urls: list[str]) -> dict[str, Any]:
    api.health_check()
    active = api.list_runs(statuses=["QUEUED", "PREPARING", "RUNNING"])
    if active:
        raise RunnerError(f"이전 TestRun workload가 처리 중입니다: {len(active)}건")
    queues = _queue_state(inspector, source_urls, dlq_urls)
    if not inspector.is_empty(queues["source"]) or not inspector.is_empty(queues["dlq"]):
        raise RunnerError("이전 테스트의 SQS source queue 또는 DLQ가 비어 있지 않습니다.")
    return {"activeRuns": 0, "queues": queues}


def _wait_for_drain(api: ApiClient, inspector: QueueInspector, source_urls: list[str],
                    started_at: datetime, timeout_seconds: float, polling_seconds: float) -> dict[str, Any]:
    drain_started = time.monotonic()
    last_queues: dict[str, Any] = {}
    while time.monotonic() - drain_started <= timeout_seconds:
        active = api.list_runs(statuses=["QUEUED", "PREPARING", "RUNNING"], created_from=started_at, created_to=_now())
        last_queues = {"source": inspector.snapshot(source_urls)}
        if not active and inspector.is_empty(last_queues["source"]):
            return {"passed": True, "duration_seconds": round(time.monotonic() - drain_started, 3),
                    "active_runs": 0, "queues": last_queues}
        time.sleep(polling_seconds)
    active = api.list_runs(statuses=["QUEUED", "PREPARING", "RUNNING"], created_from=started_at, created_to=_now())
    return {"passed": False, "duration_seconds": round(time.monotonic() - drain_started, 3),
            "active_runs": len(active), "queues": last_queues}


def _k6_environment(profile: dict[str, Any], run_id: str, suite_id: int, base_url: str) -> dict[str, str]:
    workload = profile["workload"]
    target = profile["target"]
    api = profile["acceptance"]["api"]
    completion = profile["acceptance"]["completion"]
    return {
        "PERF_BASE_URL": base_url,
        "PERF_SUITE_ID": str(suite_id),
        "PERF_TARGET_URL": target["identifier"],
        "PERF_TARGET_MODEL": target["model"],
        "PERF_TARGET_REVISION": target.get("revision", ""),
        "PERF_RUN_ID": run_id,
        "PERF_CONCURRENT_TEST_RUNS": str(workload["concurrent_test_runs"]),
        "PERF_RAMP_UP_SECONDS": str(workload["ramp_up_seconds"]),
        "PERF_DURATION_SECONDS": str(workload["duration_seconds"]),
        "PERF_MAX_ITERATIONS_PER_VU": str(workload.get("max_iterations_per_vu", 0)),
        "PERF_COMPLETION_TIMEOUT_SECONDS": str(workload["completion_timeout_seconds"]),
        "PERF_POLLING_INTERVAL_SECONDS": str(workload["polling_interval_seconds"]),
        "PERF_API_P50_MS": str(api["create_latency_ms"]["p50"]),
        "PERF_API_P95_MS": str(api["create_latency_ms"]["p95"]),
        "PERF_API_P99_MS": str(api["create_latency_ms"]["p99"]),
        "PERF_API_ERROR_RATE": str(api["error_rate"]),
        "PERF_COMPLETION_MAX_SECONDS": str(completion["max_seconds"]),
        "PERF_COMPLETION_FAILURE_RATE": str(completion["failure_rate"]),
    }


def _threshold_inputs(profile: dict[str, Any], run_id: str, suite_id: int, base_url: str) -> dict[str, str]:
    environment = _k6_environment(profile, run_id, suite_id, base_url)
    names = (
        "PERF_API_P50_MS", "PERF_API_P95_MS", "PERF_API_P99_MS", "PERF_API_ERROR_RATE",
        "PERF_COMPLETION_MAX_SECONDS", "PERF_COMPLETION_FAILURE_RATE",
    )
    return {name: environment[name] for name in names}


def run_k6(profile: dict[str, Any], run_id: str, suite_id: int, base_url: str,
           summary_path: Path, repo_root: Path) -> int:
    environment = os.environ.copy()
    environment.update(_k6_environment(profile, run_id, suite_id, base_url))
    try:
        result = subprocess.run([
            os.environ.get("K6_BIN", "k6"), "run", "--summary-export", str(summary_path),
            str(repo_root / "performance/k6/test-run.js"),
        ], cwd=repo_root, env=environment, check=False)
    except OSError as exc:
        raise RunnerError("k6 workload 실행에 실패했습니다.") from exc
    if result.returncode not in {0, K6_THRESHOLD_FAILURE_EXIT_CODE}:
        raise RunnerError(f"k6 script 또는 실행 환경 오류로 종료되었습니다 (exit {result.returncode}).")
    if not summary_path.is_file():
        raise RunnerError("k6가 summary export를 만들지 않았습니다.")
    return result.returncode


def _read_json(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RunnerError(f"JSON 결과를 읽을 수 없습니다: {path}") from exc
    if not isinstance(value, dict):
        raise RunnerError(f"JSON 결과 최상위는 object여야 합니다: {path}")
    return value


def _collect_run_diagnostics(api: ApiClient, started_at: datetime, finished_at: datetime) -> dict[str, Any]:
    runs = api.list_runs(created_from=started_at, created_to=finished_at)
    failed_runs: list[dict[str, Any]] = []
    error_counts: Counter[str] = Counter()
    for run in runs:
        run_id = run.get("id")
        if not isinstance(run_id, int):
            continue
        detail = api.get_run(run_id)
        outcome = detail.get("executionOutcome")
        if outcome == "COMPLETED":
            continue
        errors: list[dict[str, Any]] = []
        for item in api.list_run_results(run_id):
            error = item.get("error")
            if not isinstance(error, dict):
                continue
            stage = error.get("stage")
            code = error.get("code")
            if stage or code:
                error_counts[f"{stage or 'UNKNOWN'}:{code or 'UNKNOWN'}"] += 1
            errors.append({"stage": stage, "code": code})
        failed_runs.append({
            "id": run_id,
            "status": detail.get("status"),
            "executionOutcome": outcome,
            "errors": errors,
        })
    return {
        "test_run_count": len(runs),
        "failed_test_run_ids": [item["id"] for item in failed_runs],
        "failed_runs": failed_runs,
        "error_summary": dict(sorted(error_counts.items())),
    }


def _report(result: dict[str, Any]) -> str:
    profile = result["profile"]
    workload = profile["workload"]
    acceptance = profile["acceptance"]
    capacity = result["infrastructure_capacity"]
    decision = result.get("acceptance", {}).get("status", "FAIL")
    diagnostics = result.get("test_run_diagnostics", {})
    lines = [
        f"# {profile['test']['id']} performance report",
        "",
        f"- 실행 시각: {result['started_at']} ~ {result['finished_at']}",
        f"- Application revision: {result['revisions']['application']}",
        f"- Infrastructure revision: {result['revisions']['infrastructure']}",
        f"- Dataset: {result['dataset']['id']} ({result['dataset']['test_case_count']} TestCases)",
        f"- Workload: {workload['concurrent_test_runs']} concurrent TestRuns, ramp-up {workload['ramp_up_seconds']}s, duration {workload['duration_seconds']}s",
        "",
        "## Acceptance Criteria",
        "",
        f"- API latency: p50 < {acceptance['api']['create_latency_ms']['p50']}ms, p95 < {acceptance['api']['create_latency_ms']['p95']}ms, p99 < {acceptance['api']['create_latency_ms']['p99']}ms",
        f"- API error rate: <= {acceptance['api']['error_rate']}",
        f"- TestRun completion: <= {acceptance['completion']['max_seconds']}s, failure rate <= {acceptance['completion']['failure_rate']}",
        f"- Queue drain: <= {acceptance['completion']['queue_drain_seconds']}s, DLQ messages <= {acceptance['completion']['dlq_messages']}",
        "",
        "## Results",
        "",
        "- k6 summary: `k6-summary.json`",
        f"- Performance thresholds: {result['failure_categories']['thresholds']} (k6 exit {result['k6']['exit_code']})",
        f"- Business execution failures: {result['failure_categories']['execution_failures']} TestRun(s)",
        f"- Failed TestRun IDs: {diagnostics.get('failed_test_run_ids', [])}",
        f"- Execution error summary: {diagnostics.get('error_summary', {})}",
        f"- Queue drain: {result['drain']}",
        f"- AWS metrics: `aws-metrics.json` ({result['aws_metrics'].get('status')})",
        f"- Decision: **{decision}**",
        "",
        "## Infrastructure Capacity Snapshot",
        "",
        f"- Captured at: {capacity['captured_at']}",
        f"- ECS: cluster `{capacity['ecs']['cluster_identifier']}`, service `{capacity['ecs']['service_identifier']}`, desired {capacity['ecs']['desired_count']}, running {capacity['ecs']['running_task_count']}, task CPU {capacity['ecs']['task_cpu']}, task memory {capacity['ecs']['task_memory']}",
        f"- RDS: instance `{capacity['rds']['db_instance_identifier']}`, class `{capacity['rds']['db_instance_class']}`",
        f"- SageMaker: endpoint `{capacity['sagemaker']['endpoint_name']}`, variant `{capacity['sagemaker']['production_variant_name']}`, type `{capacity['sagemaker']['instance_type']}`, desired {capacity['sagemaker']['desired_instance_count']}, current {capacity['sagemaker']['current_instance_count']}",
        "- 위 값은 실행 전 configured capacity snapshot이며, 실행 중 관측값은 `aws-metrics.json`에 별도로 저장한다.",
        "",
        "## Bottleneck observations",
        "",
        "- 자동 병목 판정은 하지 않는다. k6 summary와 AWS metric 시계열을 함께 확인해 관찰을 기록한다.",
        "",
        "## Next experiment",
        "",
        "- 동일 Dataset과 Infrastructure Capacity에서 Workload만 변경해 포화 구간을 찾는다.",
        "- 인프라 효과를 볼 때는 동일 Dataset/Workload를 유지하고 Infrastructure Capacity만 변경한다.",
        "- 목표값을 바꾸어 결과를 맞추지 말고 실행 전에 Profile을 확정한다.",
    ]
    return "\n".join(lines) + "\n"


def execute(args: argparse.Namespace) -> int:
    repo_root = _repo_root()
    profile_path = Path(args.profile).resolve()
    dataset_path = Path(args.dataset).resolve()
    profile = load_profile(profile_path)
    payload, test_case_count = load_seed_payload(dataset_path)
    run_id = f"{profile['test']['id']}-{_now().strftime('%Y%m%dT%H%M%SZ')}-{uuid.uuid4().hex[:8]}"
    result_dir = Path(args.result_dir).resolve() if args.result_dir else repo_root / "performance/results" / run_id
    result_dir.mkdir(parents=True, exist_ok=False)
    shutil.copyfile(profile_path, result_dir / "profile.yaml")
    shutil.copyfile(dataset_path, result_dir / "dataset.yaml")

    if args.dry_run:
        plan = {"run_id": run_id, "profile": str(profile_path), "dataset": str(dataset_path),
                "test_case_count": test_case_count, "k6_script": str(repo_root / "performance/k6/test-run.js")}
        (result_dir / "dry-run.json").write_text(json.dumps(plan, indent=2) + "\n", encoding="utf-8")
        print(json.dumps(plan, indent=2))
        return 0

    if not args.reset:
        raise ConfigurationError(
            "Baseline 비교의 DB 상태를 고정하려면 실제 실행에 --reset을 지정해야 합니다."
        )
    revisions = {
        "application": _required_revision("APP_REVISION"),
        "infrastructure": _required_revision("INFRA_REVISION"),
    }
    base_url = os.environ.get("PERF_BASE_URL", "").strip()
    if not base_url:
        raise ConfigurationError("실제 성능 실행에는 PERF_BASE_URL이 필요합니다.")
    api = ApiClient(base_url)
    source_urls, dlq_urls = queue_urls_from_environment()
    inspector = QueueInspector()
    preflight = _assert_preflight(api, inspector, source_urls, dlq_urls)
    infrastructure_capacity = InfrastructureCapacityCollector().collect()
    infrastructure_capacity["revisions"] = revisions
    environment = dict(os.environ)
    reset_database(environment)
    apply_migrations(environment, repo_root / "src/main/resources/db/migration")

    api.health_check()
    suite_id = api.create_suite(payload)
    if suite_id <= 0:
        raise RunnerError("seed 결과의 TestSuite id가 양수가 아닙니다.")

    started_at = _now()
    summary_path = result_dir / "k6-summary.json"
    threshold_inputs = _threshold_inputs(profile, run_id, suite_id, base_url)
    k6_exit_code = run_k6(profile, run_id, suite_id, base_url, summary_path, repo_root)
    summary = _read_json(summary_path)
    workload = profile["workload"]
    drain = _wait_for_drain(api, inspector, source_urls, started_at,
                            workload["completion_timeout_seconds"], workload["polling_interval_seconds"])
    finished_at = _now()
    final_queues = inspector.snapshot(dlq_urls)
    metrics = CloudWatchMetricCollector(repo_root / "performance/metrics/aws.yaml").collect(started_at, finished_at)
    (result_dir / "aws-metrics.json").write_text(json.dumps(metrics, indent=2, default=str) + "\n", encoding="utf-8")
    acceptance = evaluate(
        profile, summary, drain, final_queues, metrics,
        k6_exit_code == K6_THRESHOLD_FAILURE_EXIT_CODE,
    )
    diagnostics = _collect_run_diagnostics(api, started_at, finished_at)
    threshold_failed = k6_exit_code == K6_THRESHOLD_FAILURE_EXIT_CODE
    result = {
        "run_id": run_id,
        "started_at": _iso(started_at),
        "finished_at": _iso(finished_at),
        "revisions": revisions,
        "infrastructure_capacity": infrastructure_capacity,
        "dataset": {"id": load_dataset(dataset_path).get("id", dataset_path.stem), "test_case_count": test_case_count,
                    "suite_id": suite_id},
        "profile": profile,
        "k6": {
            "exit_code": k6_exit_code,
            "thresholds": "FAIL" if threshold_failed else "PASS",
            "threshold_inputs": threshold_inputs,
        },
        "failure_categories": {
            "thresholds": "FAIL" if threshold_failed else "PASS",
            "execution_failures": len(diagnostics["failed_test_run_ids"]),
        },
        "test_run_diagnostics": diagnostics,
        "preflight": preflight,
        "drain": drain,
        "final_dlq": final_queues,
        "aws_metrics": metrics,
        "acceptance": acceptance,
    }
    (result_dir / "result.json").write_text(json.dumps(result, indent=2, default=str) + "\n", encoding="utf-8")
    (result_dir / "report.md").write_text(_report(result), encoding="utf-8")
    bucket = os.environ.get("PERFORMANCE_RESULTS_BUCKET")
    if bucket:
        try:
            upload_result_directory(result_dir, bucket, run_id)
        except ResultUploadError as exc:
            raise RunnerError("Performance 결과 S3 보존에 실패했습니다. 로컬 결과는 유지됩니다.") from exc
    else:
        print("PERFORMANCE_RESULTS_BUCKET이 없어 S3 결과 업로드를 건너뜁니다.", file=sys.stderr)
    print(f"{acceptance['status']}: {result_dir}")
    return 0 if acceptance["status"] == "PASS" else 1


def parser() -> argparse.ArgumentParser:
    root = _repo_root()
    command = argparse.ArgumentParser(description="Run a repeatable GuardBench performance profile.")
    command.add_argument("--profile", default=str(root / "performance/profiles/smoke.yaml"))
    command.add_argument("--dataset", default=str(root / "performance/datasets/baseline-v1.yaml"))
    command.add_argument("--result-dir")
    command.add_argument("--reset", action="store_true", help="Reset and migrate only a guarded performance DB.")
    command.add_argument("--dry-run", action="store_true", help="Validate inputs and print the execution plan.")
    return command


def main() -> int:
    try:
        return execute(parser().parse_args())
    except (ConfigurationError, RunnerError, ApiError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
