# API 공통 응답 DTO

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-23
> Canonical source: GitHub
> Origin: [Notion API 공통 응답 DTO](https://app.notion.com/p/3c1eeed6b62d81e7abe2eea3d730c611)
> AI assistance: 이 문서의 초안은 LLM의 도움으로 작성되었으며 사람의 검토가 필요합니다.

응답 Body가 존재하는 성공과 실패는 `ApiResponse<T>`를 사용한다. 최상위 필드는 `httpStatus`, `message`, `data`다. 204 No Content에는 Body가 없다.

```json
{
  "httpStatus": 200,
  "message": "요청이 정상적으로 처리되었습니다.",
  "data": {}
}
```

- `httpStatus`는 실제 HTTP status와 같은 숫자다. 예: HTTP 201이면 `201`.
- `message`는 사용자 안내용이며 클라이언트 분기 기준이 아니다.
- 성공 `data`에는 API 전용 Response DTO를 둔다. 예: `TestRunResultRes`.
- 실패 `data`에는 안정적인 `code`를 가진 Error Detail을 둔다.

```json
{
  "httpStatus": 404,
  "message": "TestRun을 찾을 수 없습니다.",
  "data": { "code": "TEST_RUN_NOT_FOUND" }
}
```

Validation 오류는 한 필드의 여러 오류를 보존하도록 목록을 사용한다.

```json
{
  "httpStatus": 400,
  "message": "요청 값이 올바르지 않습니다.",
  "data": {
    "code": "VALIDATION_ERROR",
    "errors": [{ "field": "name", "message": "이름은 필수입니다." }]
  }
}
```

HTTP 상태와 GuardBench 상태를 구분한다. 예: 조회가 성공하면 `qualityGateStatus: "FAIL"`이어도 HTTP 200이다.
