"""Dataset fixture parsing and seed payload construction."""

from __future__ import annotations

import json
import hashlib
from pathlib import Path
from typing import Any

from .config import ConfigurationError, load_dataset


def load_seed_payload(manifest_path: Path) -> tuple[dict[str, Any], int]:
    manifest = load_dataset(manifest_path)
    source_path = (manifest_path.parent / manifest["source"]["path"]).resolve()
    try:
        source_bytes = source_path.read_bytes()
        source = source_bytes.decode("utf-8")
    except (OSError, UnicodeDecodeError) as exc:
        raise ConfigurationError(f"Dataset source를 읽을 수 없습니다: {source_path}") from exc
    expected_sha256 = manifest["source"].get("sha256")
    actual_sha256 = hashlib.sha256(source_bytes).hexdigest()
    if expected_sha256 and actual_sha256 != expected_sha256:
        raise ConfigurationError(f"Dataset source checksum이 baseline과 다릅니다: {source_path}")

    start = source.find("\n{")
    if start < 0:
        start = source.find("{")
    if start < 0:
        raise ConfigurationError(f"JSON seed payload를 찾을 수 없습니다: {source_path}")
    try:
        payload, _ = json.JSONDecoder().raw_decode(source[start + (1 if source[start] == "\n" else 0):])
    except json.JSONDecodeError as exc:
        raise ConfigurationError(f"Dataset source의 JSON payload가 올바르지 않습니다: {source_path}") from exc
    test_cases = payload.get("testCases")
    if not isinstance(test_cases, list):
        raise ConfigurationError("Dataset seed payload에 testCases 배열이 없습니다.")
    expected = manifest["source"]["expected_test_case_count"]
    if len(test_cases) != expected:
        raise ConfigurationError(f"Dataset {manifest['id']}의 TestCase 수가 {expected}가 아닙니다: {len(test_cases)}")
    return payload, len(test_cases)
