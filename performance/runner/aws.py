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


def _required_environment(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        raise ConfigurationError(f"Infrastructure Capacity snapshot에 {name}이(가) 필요합니다.")
    return value


def _required_response_field(response: dict[str, Any], field: str, resource: str) -> Any:
    value = response.get(field)
    if value is None or (isinstance(value, str) and not value.strip()):
        raise ConfigurationError(f"{resource} 조회 응답에 {field}가 없습니다.")
    return value


class InfrastructureCapacityCollector:
    """Collect the configured AWS capacity immediately before a workload starts."""

    def __init__(self, region: str | None = None, *, ecs_client=None, rds_client=None,
                 sagemaker_client=None) -> None:
        boto3 = _boto3() if any(client is None for client in (ecs_client, rds_client, sagemaker_client)) else None
        region_name = region or os.environ.get("AWS_REGION", "ap-northeast-2")
        self.ecs_client = ecs_client or boto3.client("ecs", region_name=region_name)
        self.rds_client = rds_client or boto3.client("rds", region_name=region_name)
        self.sagemaker_client = sagemaker_client or boto3.client("sagemaker", region_name=region_name)

    def collect(self) -> dict[str, Any]:
        cluster = _required_environment("PERF_ECS_CLUSTER")
        service = _required_environment("PERF_ECS_SERVICE")
        rds_instance = _required_environment("PERF_RDS_INSTANCE_ID")
        endpoint = _required_environment("PERF_SAGEMAKER_ENDPOINT_NAME")
        variant = _required_environment("PERF_SAGEMAKER_VARIANT_NAME")
        try:
            ecs_service = self._ecs_service(cluster, service)
            task_definition_arn = _required_response_field(ecs_service, "taskDefinition", "ECS service")
            task_definition = self.ecs_client.describe_task_definition(
                taskDefinition=task_definition_arn,
            ).get("taskDefinition", {})
            if not isinstance(task_definition, dict):
                raise ConfigurationError("ECS task definition 조회 응답이 올바르지 않습니다.")

            rds_response = self.rds_client.describe_db_instances(
                DBInstanceIdentifier=rds_instance,
            )
            rds_instances = rds_response.get("DBInstances", [])
            if len(rds_instances) != 1 or not isinstance(rds_instances[0], dict):
                raise ConfigurationError("RDS instance 조회 결과가 하나가 아닙니다.")
            rds = rds_instances[0]

            endpoint_response = self.sagemaker_client.describe_endpoint(EndpointName=endpoint)
            variants = endpoint_response.get("ProductionVariants", [])
            selected_variant = next(
                (item for item in variants if isinstance(item, dict) and item.get("VariantName") == variant),
                None,
            )
            if selected_variant is None:
                raise ConfigurationError(f"SageMaker endpoint에 production variant가 없습니다: {variant}")

            endpoint_config_name = _required_response_field(
                endpoint_response, "EndpointConfigName", "SageMaker endpoint"
            )
            endpoint_config_response = self.sagemaker_client.describe_endpoint_config(
                EndpointConfigName=endpoint_config_name,
            )
            config_variants = endpoint_config_response.get("ProductionVariants", [])
            selected_config_variant = next(
                (item for item in config_variants
                 if isinstance(item, dict) and item.get("VariantName") == variant),
                None,
            )
            if selected_config_variant is None:
                raise ConfigurationError(f"SageMaker endpoint config에 production variant가 없습니다: {variant}")

            desired_count = selected_variant.get("DesiredInstanceCount")
            current_count = selected_variant.get("CurrentInstanceCount")
            initial_count = selected_variant.get("InitialInstanceCount")
            if desired_count is None and current_count is None and initial_count is None:
                raise ConfigurationError("SageMaker production variant에 instance count가 없습니다.")

            return {
                "captured_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
                "ecs": {
                    "cluster_identifier": cluster,
                    "service_identifier": service,
                    "desired_count": _required_response_field(ecs_service, "desiredCount", "ECS service"),
                    "running_task_count": _required_response_field(ecs_service, "runningCount", "ECS service"),
                    "task_cpu": _required_response_field(task_definition, "cpu", "ECS task definition"),
                    "task_memory": _required_response_field(task_definition, "memory", "ECS task definition"),
                },
                "rds": {
                    "db_instance_identifier": _required_response_field(
                        rds, "DBInstanceIdentifier", "RDS instance"
                    ),
                    "db_instance_class": _required_response_field(rds, "DBInstanceClass", "RDS instance"),
                },
                "sagemaker": {
                    "endpoint_name": endpoint,
                    "production_variant_name": _required_response_field(
                        selected_variant, "VariantName", "SageMaker production variant"
                    ),
                    "instance_type": _required_response_field(
                        selected_config_variant, "InstanceType", "SageMaker endpoint config production variant"
                    ),
                    "desired_instance_count": desired_count,
                    "current_instance_count": current_count,
                    "initial_instance_count": initial_count,
                },
            }
        except ConfigurationError:
            raise
        except Exception as exc:
            raise ConfigurationError("Infrastructure Capacity 조회에 실패했습니다.") from exc

    def _ecs_service(self, cluster: str, service: str) -> dict[str, Any]:
        response = self.ecs_client.describe_services(cluster=cluster, services=[service])
        services = response.get("services", [])
        if len(services) != 1 or not isinstance(services[0], dict):
            raise ConfigurationError("ECS service 조회 결과가 하나가 아닙니다.")
        return services[0]


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
