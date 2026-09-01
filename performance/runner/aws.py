"""AWS preflight and metric collection kept outside the k6 workload."""

from __future__ import annotations

import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from .config import ConfigurationError, load_yaml


def _boto3():
    try:
        import boto3
    except ImportError as exc:  # pragma: no cover - exercised in a missing-tool installation
        raise ConfigurationError("AWS metric 수집에는 performance/requirements.txt 설치가 필요합니다.") from exc
    return boto3


def queue_urls_from_environment() -> tuple[list[str], list[str]]:
    source = [item.strip() for item in os.environ.get("PERF_SOURCE_QUEUE_URLS", "").split(",") if item.strip()]
    dlq = [item.strip() for item in os.environ.get("PERF_DLQ_URLS", "").split(",") if item.strip()]
    if not source or not dlq:
        raise ConfigurationError("PERF_SOURCE_QUEUE_URLS와 PERF_DLQ_URLS를 모두 설정해야 합니다.")
    return source, dlq


class QueueInspector:
    def __init__(self, region: str | None = None) -> None:
        boto3 = _boto3()
        self.client = boto3.client(
            "sqs",
            region_name=region or os.environ.get("AWS_REGION", "ap-northeast-2"),
            endpoint_url=os.environ.get("PERF_SQS_ENDPOINT_URL") or None,
        )

    def snapshot(self, queue_urls: list[str]) -> list[dict[str, Any]]:
        result = []
        for queue_url in queue_urls:
            try:
                response = self.client.get_queue_attributes(
                    QueueUrl=queue_url,
                    AttributeNames=[
                        "ApproximateNumberOfMessages",
                        "ApproximateNumberOfMessagesNotVisible",
                        "ApproximateNumberOfMessagesDelayed",
                        "ApproximateAgeOfOldestMessage",
                    ],
                )
            except Exception as exc:
                raise ConfigurationError("SQS queue 상태 조회에 실패했습니다.") from exc
            attributes = response.get("Attributes", {})
            result.append({
                "queueUrl": queue_url,
                "visible": int(attributes.get("ApproximateNumberOfMessages", 0)),
                "notVisible": int(attributes.get("ApproximateNumberOfMessagesNotVisible", 0)),
                "delayed": int(attributes.get("ApproximateNumberOfMessagesDelayed", 0)),
                "oldestAgeSeconds": int(attributes.get("ApproximateAgeOfOldestMessage", 0)),
            })
        return result

    @staticmethod
    def is_empty(snapshot: list[dict[str, Any]], *, include_in_flight: bool = True) -> bool:
        for queue in snapshot:
            if queue["visible"] or queue["delayed"]:
                return False
            if include_in_flight and queue["notVisible"]:
                return False
        return True


class CloudWatchMetricCollector:
    def __init__(self, config_path: Path, region: str | None = None) -> None:
        self.config = load_yaml(config_path)
        boto3 = _boto3()
        self.client = boto3.client(
            "cloudwatch",
            region_name=region or os.environ.get("AWS_REGION", "ap-northeast-2"),
            endpoint_url=os.environ.get("PERF_CLOUDWATCH_ENDPOINT_URL") or None,
        )

    def collect(self, started_at: datetime, finished_at: datetime) -> dict[str, Any]:
        definitions = self.config.get("metrics", [])
        queries = []
        included = []
        skipped = []
        for definition in definitions:
            dimensions = definition.get("dimensions", {})
            if any(not str(value).strip() or "${" in str(value) for value in dimensions.values()):
                skipped.append({"id": definition.get("id"), "reason": "필수 resource dimension 미설정"})
                continue
            query_id = "q" + "".join(char if char.isalnum() else "_" for char in definition["id"]).lower()
            queries.append({
                "Id": query_id,
                "MetricStat": {
                    "Metric": {
                        "Namespace": definition["namespace"],
                        "MetricName": definition["metric_name"],
                        "Dimensions": [{"Name": key, "Value": str(value)} for key, value in dimensions.items()],
                    },
                    "Period": int(definition.get("period_seconds", 60)),
                    "Stat": definition.get("statistic", "Average"),
                },
                "ReturnData": True,
            })
            included.append(definition["id"])

        if not queries:
            return {"status": "NOT_CONFIGURED", "metrics": [], "skipped": skipped}
        try:
            response = self.client.get_metric_data(
                MetricDataQueries=queries,
                StartTime=started_at.astimezone(timezone.utc),
                EndTime=finished_at.astimezone(timezone.utc),
                ScanBy="TimestampDescending",
            )
        except Exception as exc:
            raise ConfigurationError("CloudWatch metric 조회에 실패했습니다.") from exc
        by_id = {item["Id"]: item for item in response.get("MetricDataResults", [])}
        metrics = []
        for definition_id in included:
            query_id = "q" + "".join(char if char.isalnum() else "_" for char in definition_id).lower()
            item = by_id.get(query_id, {})
            metrics.append({
                "id": definition_id,
                "label": item.get("Label"),
                "timestamps": [value.isoformat() for value in item.get("Timestamps", [])],
                "values": item.get("Values", []),
                "status": item.get("StatusCode"),
            })
        return {"status": "COLLECTED", "metrics": metrics, "skipped": skipped}
