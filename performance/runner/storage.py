"""S3 persistence for completed performance-run artifacts."""

from __future__ import annotations

from pathlib import Path

import boto3
from botocore.exceptions import BotoCoreError, ClientError


RESULT_FILENAMES = (
    "profile.yaml",
    "dataset.yaml",
    "k6-summary.json",
    "aws-metrics.json",
    "result.json",
    "report.md",
)


class ResultUploadError(RuntimeError):
    """Raised when a completed result cannot be persisted to S3."""


def upload_result_directory(result_dir: Path, bucket: str, run_id: str, *, s3_client=None) -> None:
    if not bucket.strip():
        raise ResultUploadError("PERFORMANCE_RESULTS_BUCKET이 비어 있습니다.")
    missing = [name for name in RESULT_FILENAMES if not (result_dir / name).is_file()]
    if missing:
        raise ResultUploadError(f"업로드할 완료 결과 파일이 없습니다: {', '.join(missing)}")
    client = s3_client or boto3.client("s3")
    try:
        for filename in RESULT_FILENAMES:
            client.upload_file(str(result_dir / filename), bucket, f"performance/results/{run_id}/{filename}")
    except (BotoCoreError, ClientError, OSError) as exc:
        raise ResultUploadError("S3 결과 업로드에 실패했습니다.") from exc
