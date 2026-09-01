"""Non-bypassable guards for the destructive performance database reset."""

from __future__ import annotations

from typing import Mapping

from .config import ConfigurationError


def validate_reset_safety(environment: Mapping[str, str]) -> None:
    expected = {
        "PERFORMANCE_ENVIRONMENT": "performance",
        "PERFORMANCE_DB_NAME": "guardbench_perf",
        "PERFORMANCE_DB_IDENTIFIER": "guardbench-perf",
        "PERFORMANCE_RESET_CONFIRM": "RESET_GUARDBENCH_PERF",
    }
    missing_or_wrong = [key for key, value in expected.items() if environment.get(key) != value]
    if missing_or_wrong:
        raise ConfigurationError(
            "DB reset을 중단했습니다. performance 환경, guardbench_perf DB, "
            "guardbench-perf 식별자와 RESET_GUARDBENCH_PERF 확인이 모두 필요합니다: "
            + ", ".join(missing_or_wrong)
        )
