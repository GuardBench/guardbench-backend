"""Non-bypassable guards for the destructive performance database reset."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping
from urllib.parse import unquote, urlparse

from .config import ConfigurationError


EXPECTED_DATABASE_NAME = "guardbench_perf"


@dataclass(frozen=True)
class DatabaseTarget:
    host: str
    port: int
    database: str


def database_target(database_url: str, *, jdbc: bool = False) -> DatabaseTarget:
    if jdbc and not database_url.startswith("jdbc:"):
        raise ConfigurationError("PERFORMANCE_DATABASE_JDBC_URL은 jdbc:postgresql URL이어야 합니다.")
    raw_url = database_url.removeprefix("jdbc:") if jdbc else database_url
    parsed = urlparse(raw_url)
    if parsed.scheme not in {"postgres", "postgresql"} or not parsed.hostname or parsed.fragment:
        raise ConfigurationError(
            "database name이 포함된 PostgreSQL connection URL이어야 합니다."
        )
    database = unquote(parsed.path.lstrip("/"))
    if not database:
        raise ConfigurationError("PostgreSQL connection URL에 database name이 없습니다.")
    try:
        port = parsed.port or 5432
    except ValueError as exc:
        raise ConfigurationError("PostgreSQL connection URL의 port가 올바르지 않습니다.") from exc
    return DatabaseTarget(parsed.hostname.rstrip(".").lower(), port, database)


def migration_jdbc_url(environment: Mapping[str, str]) -> str:
    database_url = environment.get("PERFORMANCE_DATABASE_URL")
    if not database_url:
        raise ConfigurationError("migration에는 PERFORMANCE_DATABASE_URL이 필요합니다.")
    reset_target = database_target(database_url)
    configured_jdbc_url = environment.get("PERFORMANCE_DATABASE_JDBC_URL")
    if configured_jdbc_url:
        migration_target = database_target(configured_jdbc_url, jdbc=True)
        if migration_target != reset_target:
            raise ConfigurationError(
                "PERFORMANCE_DATABASE_JDBC_URL의 host, port, database는 "
                "PERFORMANCE_DATABASE_URL과 같아야 합니다."
            )
        return configured_jdbc_url

    parsed = urlparse(database_url)
    host = parsed.hostname or ""
    host_for_url = f"[{host}]" if ":" in host else host
    port = reset_target.port
    query = f"?{parsed.query}" if parsed.query else ""
    return f"jdbc:postgresql://{host_for_url}:{port}{parsed.path}{query}"


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
    if database_target(database_url).database != EXPECTED_DATABASE_NAME:
        raise ConfigurationError(
            "DB reset을 중단했습니다. PERFORMANCE_DATABASE_URL의 실제 database name이 "
            f"{EXPECTED_DATABASE_NAME}이어야 합니다."
        )
