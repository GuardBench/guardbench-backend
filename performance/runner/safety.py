"""Non-bypassable guards for the destructive performance database reset."""

from __future__ import annotations

from typing import Mapping
from urllib.parse import unquote, urlparse

from .config import ConfigurationError


EXPECTED_DATABASE_NAME = "guardbench_perf"


def _database_name(database_url: str) -> str:
    parsed = urlparse(database_url)
    if parsed.scheme not in {"postgres", "postgresql"} or not parsed.hostname or parsed.fragment:
        raise ConfigurationError(
            "PERFORMANCE_DATABASE_URL은 database name이 포함된 PostgreSQL URL이어야 합니다."
        )
    name = unquote(parsed.path.lstrip("/"))
    if not name:
        raise ConfigurationError("PERFORMANCE_DATABASE_URL에 database name이 없습니다.")
    return name


def validate_reset_safety(environment: Mapping[str, str], database_url: str | None = None) -> None:
    database_url = database_url or environment.get("PERFORMANCE_DATABASE_URL")
    expected = {
        "PERFORMANCE_ENVIRONMENT": "performance",
        "PERFORMANCE_DB_NAME": EXPECTED_DATABASE_NAME,
        "PERFORMANCE_RESET_CONFIRM": "RESET_GUARDBENCH_PERF",
    }
    missing_or_wrong = [key for key, value in expected.items() if environment.get(key) != value]
    if missing_or_wrong:
        raise ConfigurationError(
            "DB reset을 중단했습니다. performance 환경, guardbench_perf DB와 "
            "RESET_GUARDBENCH_PERF 확인이 필요합니다: "
            + ", ".join(missing_or_wrong)
        )
    if not database_url:
        raise ConfigurationError("DB reset에는 PERFORMANCE_DATABASE_URL이 필요합니다.")
    if _database_name(database_url) != EXPECTED_DATABASE_NAME:
        raise ConfigurationError(
            "DB reset을 중단했습니다. PERFORMANCE_DATABASE_URL의 실제 database name이 "
            f"{EXPECTED_DATABASE_NAME}이어야 합니다."
        )
