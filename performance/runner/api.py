"""Small stdlib HTTP client for the public GuardBench API."""

from __future__ import annotations

import json
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from typing import Any


class ApiError(RuntimeError):
    pass


class ApiClient:
    def __init__(self, base_url: str, timeout: float = 30) -> None:
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def request(self, method: str, path: str, body: dict[str, Any] | None = None) -> tuple[int, dict[str, Any]]:
        data = json.dumps(body).encode("utf-8") if body is not None else None
        request = urllib.request.Request(
            self.base_url + path,
            data=data,
            method=method,
            headers={"Accept": "application/json", "Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                raw = response.read()
                status = response.status
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError) as exc:
            status = getattr(exc, "code", 0)
            raise ApiError(f"GuardBench API 요청 실패: {method} {path} (HTTP {status})") from exc
        try:
            return status, json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ApiError(f"GuardBench API가 JSON이 아닌 응답을 반환했습니다: {method} {path}") from exc

    def health_check(self) -> None:
        status, _ = self.request("GET", "/api/v1/test-suites?page=1&size=1")
        if status != 200:
            raise ApiError(f"GuardBench API health check가 HTTP {status}를 반환했습니다.")

    def list_runs(self, statuses: list[str] | None = None,
                  created_from: datetime | None = None,
                  created_to: datetime | None = None) -> list[dict[str, Any]]:
        items: list[dict[str, Any]] = []
        page = 1
        while True:
            params: list[tuple[str, str]] = [("page", str(page)), ("size", "100")]
            for status in statuses or []:
                params.append(("status", status))
            if created_from:
                params.append(("createdFrom", created_from.isoformat().replace("+00:00", "Z")))
            if created_to:
                params.append(("createdTo", created_to.isoformat().replace("+00:00", "Z")))
            _, body = self.request("GET", "/api/v1/test-runs?" + urllib.parse.urlencode(params))
            data = body.get("data", {})
            if not isinstance(data, dict):
                return items
            page_items = data.get("items", [])
            if isinstance(page_items, list):
                items.extend(item for item in page_items if isinstance(item, dict))
            page_meta = data.get("page", {})
            if not isinstance(page_meta, dict) or page_meta.get("hasNext") is not True:
                return items
            page += 1

    def get_run(self, test_run_id: int) -> dict[str, Any]:
        status, body = self.request("GET", f"/api/v1/test-runs/{test_run_id}")
        if status != 200 or not isinstance(body.get("data"), dict):
            raise ApiError(f"TestRun 상세 조회 응답이 올바르지 않습니다: {test_run_id}")
        return body["data"]

    def list_run_results(self, test_run_id: int) -> list[dict[str, Any]]:
        items: list[dict[str, Any]] = []
        page = 1
        while True:
            params = urllib.parse.urlencode({"page": page, "size": 100})
            status, body = self.request("GET", f"/api/v1/test-runs/{test_run_id}/results?{params}")
            if status != 200:
                raise ApiError(f"TestRun 결과 조회가 HTTP {status}를 반환했습니다: {test_run_id}")
            data = body.get("data", {})
            if not isinstance(data, dict):
                return items
            page_items = data.get("items", [])
            if isinstance(page_items, list):
                items.extend(item for item in page_items if isinstance(item, dict))
            page_meta = data.get("page", {})
            if not isinstance(page_meta, dict) or page_meta.get("hasNext") is not True:
                return items
            page += 1

    def create_suite(self, payload: dict[str, Any]) -> int:
        status, body = self.request("POST", "/api/v1/test-suites", payload)
        if status != 201:
            raise ApiError(f"Dataset seed가 HTTP {status}로 거부되었습니다.")
        try:
            suite_id = int(body["data"]["id"])
            test_case_count = int(body["data"]["testCaseCount"])
        except (KeyError, TypeError, ValueError) as exc:
            raise ApiError("Dataset seed 응답에 TestSuite id가 없습니다.") from exc
        if test_case_count != len(payload.get("testCases", [])):
            raise ApiError("Dataset seed 응답의 TestCase 수가 요청과 다릅니다.")
        return suite_id
