"""Profile and Dataset loading with strict, environment-driven configuration."""

from __future__ import annotations

import os
import re
from pathlib import Path
from typing import Any
from urllib.parse import urlparse

import yaml


class ConfigurationError(ValueError):
    """Raised when a profile or dataset cannot be safely executed."""


_ENV_PATTERN = re.compile(r"\$\{([A-Z0-9_]+)(?::-([^}]*))?}")


def expand_environment(value: Any) -> Any:
    if isinstance(value, dict):
        return {key: expand_environment(item) for key, item in value.items()}
    if isinstance(value, list):
        return [expand_environment(item) for item in value]
    if not isinstance(value, str):
        return value

    def replace(match: re.Match[str]) -> str:
        return os.environ.get(match.group(1), match.group(2) or "")

    return _ENV_PATTERN.sub(replace, value)


def load_yaml(path: Path) -> dict[str, Any]:
    try:
        value = yaml.safe_load(path.read_text(encoding="utf-8"))
    except (OSError, yaml.YAMLError) as exc:
        raise ConfigurationError(f"설정 파일을 읽을 수 없습니다: {path}") from exc
    if not isinstance(value, dict):
        raise ConfigurationError(f"설정 파일 최상위는 object여야 합니다: {path}")
    return expand_environment(value)


def _number(value: Any, path: str, *, minimum: float = 0) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)) or value < minimum:
        raise ConfigurationError(f"{path}는 {minimum} 이상인 숫자여야 합니다.")
    return value


def _integer(value: Any, path: str, *, minimum: int = 0) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < minimum:
        raise ConfigurationError(f"{path}는 {minimum} 이상인 정수여야 합니다.")
    return value


def validate_profile(profile: dict[str, Any]) -> None:
    test = profile.get("test")
    workload = profile.get("workload")
    target = profile.get("target")
    acceptance = profile.get("acceptance")
    if not isinstance(test, dict) or not test.get("id") or not test.get("name"):
        raise ConfigurationError("test.id와 test.name은 필수입니다.")
    if test.get("type") not in {"SMOKE", "LOAD", "PEAK", "STRESS", "SOAK"}:
        raise ConfigurationError("test.type은 SMOKE, LOAD, PEAK, STRESS, SOAK 중 하나여야 합니다.")
    if not isinstance(workload, dict):
        raise ConfigurationError("workload는 필수 object입니다.")
    for field in ("concurrent_test_runs", "ramp_up_seconds", "duration_seconds",
                  "completion_timeout_seconds", "polling_interval_seconds"):
        if field not in workload:
            raise ConfigurationError(f"workload.{field}는 필수입니다.")
    _integer(workload["concurrent_test_runs"], "workload.concurrent_test_runs", minimum=1)
    _number(workload["ramp_up_seconds"], "workload.ramp_up_seconds")
    _number(workload["duration_seconds"], "workload.duration_seconds", minimum=1)
    _number(workload["completion_timeout_seconds"], "workload.completion_timeout_seconds", minimum=1)
    _number(workload["polling_interval_seconds"], "workload.polling_interval_seconds", minimum=0.1)
    if "max_iterations_per_vu" in workload:
        _integer(workload["max_iterations_per_vu"], "workload.max_iterations_per_vu")

    if not isinstance(target, dict) or target.get("type") != "HTTP_ENDPOINT":
        raise ConfigurationError("target.type은 HTTP_ENDPOINT여야 합니다.")
    for field in ("identifier", "model"):
        if not str(target.get(field, "")).strip():
            raise ConfigurationError(f"target.{field}는 환경변수 포함 비어 있지 않은 값이어야 합니다.")
    parsed_target = urlparse(target["identifier"])
    if parsed_target.scheme not in {"http", "https"} or not parsed_target.netloc \
            or parsed_target.username or parsed_target.password or parsed_target.fragment:
        raise ConfigurationError("target.identifier는 userinfo와 fragment가 없는 HTTP/HTTPS URL이어야 합니다.")
    evaluation_profile = target.get("evaluation_profile")
    if not isinstance(evaluation_profile, dict) or not isinstance(evaluation_profile.get("checks"), list) \
            or not evaluation_profile.get("checks") \
            or evaluation_profile.get("strictness") not in {"RELAXED", "STANDARD", "STRICT"}:
        raise ConfigurationError("target.evaluation_profile의 checks와 strictness가 올바르지 않습니다.")
    if any(check not in {"PROMPT_INJECTION", "PII_LEAKAGE", "HARMFUL_CONTENT"}
           for check in evaluation_profile["checks"]):
        raise ConfigurationError("지원하지 않는 evaluation_profile.checks가 있습니다.")

    if not isinstance(acceptance, dict):
        raise ConfigurationError("acceptance는 필수 object입니다.")
    api = acceptance.get("api", {})
    completion = acceptance.get("completion", {})
    for field in ("p50", "p95", "p99"):
        _number(api.get("create_latency_ms", {}).get(field), f"acceptance.api.create_latency_ms.{field}", minimum=0.001)
    _number(api.get("error_rate"), "acceptance.api.error_rate")
    if api["error_rate"] > 1:
        raise ConfigurationError("acceptance.api.error_rate는 0과 1 사이여야 합니다.")
    _number(completion.get("max_seconds"), "acceptance.completion.max_seconds", minimum=0.001)
    _number(completion.get("failure_rate"), "acceptance.completion.failure_rate")
    if completion["failure_rate"] > 1:
        raise ConfigurationError("acceptance.completion.failure_rate는 0과 1 사이여야 합니다.")
    _number(completion.get("queue_drain_seconds"), "acceptance.completion.queue_drain_seconds", minimum=0.001)
    _number(completion.get("dlq_messages"), "acceptance.completion.dlq_messages")


def load_profile(path: Path) -> dict[str, Any]:
    profile = load_yaml(path)
    validate_profile(profile)
    return profile


def load_dataset(path: Path) -> dict[str, Any]:
    dataset = load_yaml(path)
    source = dataset.get("source")
    if not dataset.get("id") or dataset.get("immutable") is not True:
        raise ConfigurationError("Dataset은 id와 immutable: true를 가져야 합니다.")
    if not isinstance(source, dict) or source.get("type") != "http-testset" or not source.get("path"):
        raise ConfigurationError("Dataset source는 http-testset path를 가져야 합니다.")
    expected = source.get("expected_test_case_count")
    if isinstance(expected, bool) or not isinstance(expected, int) or expected < 1:
        raise ConfigurationError("Dataset의 expected_test_case_count가 올바르지 않습니다.")
    sha256 = source.get("sha256")
    if not isinstance(sha256, str) or not re.fullmatch(r"[0-9a-f]{64}", sha256):
        raise ConfigurationError("immutable Dataset source에는 sha256 checksum이 필요합니다.")
    return dataset
