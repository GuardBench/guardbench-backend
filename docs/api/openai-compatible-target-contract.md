# OpenAI-compatible HTTP Target 공개 계약

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-01
> Related: #128

MVP의 `HTTP_ENDPOINT`는 OpenAI-compatible chat completions 계약만 지원한다.

```json
{
  "type": "HTTP_ENDPOINT",
  "identifier": "https://example.com/v1/chat/completions",
  "model": "example-model",
  "revision": "optional-application-revision"
}
```

`type`, `identifier`, `model`은 필수이며 `revision`만 선택이다.

Application 호출 body는 다음과 같다.

```json
{
  "model": "example-model",
  "messages": [
    {"role": "user", "content": "<TestCaseSnapshot.input>"}
  ]
}
```

성공 응답의 `choices[0].message.content`가 비어 있지 않은 문자열이어야 한다. Generic `{"input": ...}` / `{"response": ...}` 계약은 지원하지 않는다.

이 문서는 #128 변경 중 공개 계약을 명확히 고정하기 위한 보조 문서이며 최종 schema는 `openapi.yaml`에 동일하게 반영한다.
