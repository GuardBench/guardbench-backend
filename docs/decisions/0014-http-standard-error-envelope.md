# 0014. HTTP 표준 오류 응답 Envelope

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-06
> Canonical source: GitHub
> Origin: GitHub Issue #24

- ADR Status: ACCEPTED
- Decision date: 2026-09-06
- Related Issue: #24

## Context

GuardBench는 응답 Body가 있는 실패를 `httpStatus`, `message`, `data.code`로 구성된 공통
Envelope로 반환한다. 그러나 Spring MVC가 라우팅과 콘텐츠 협상 단계에서 만드는 다음 오류는
승인된 Application Error Code가 없어 Spring `ProblemDetail` 응답으로 남아 있다.

- 등록되지 않은 API 경로의 `404 Not Found`
- 지원하지 않는 HTTP Method의 `405 Method Not Allowed`
- 제공할 수 없는 응답 형식을 요청한 `406 Not Acceptable`
- 지원하지 않는 요청 Content-Type의 `415 Unsupported Media Type`

이 때문에 클라이언트는 같은 API의 오류를 두 형식으로 파싱해야 한다. 실제 Frontend
`apiClient`도 공통 Envelope를 전제로 하므로 `ProblemDetail`을 안정적인 오류 code로 분기하지
못한다. 반면 네 오류를 하나의 `400 VALIDATION_ERROR`로 축소하면 HTTP 의미와 프로토콜
헤더를 잃는다.

## Decision

네 HTTP 표준 오류의 Status 의미를 유지하면서 GuardBench 공통 Error Envelope로 통일한다.
각 오류에 다음 Application Error Code를 추가한다.

| Code | HTTP | 의미 | 기본 message |
| --- | ---: | --- | --- |
| `ENDPOINT_NOT_FOUND` | 404 | 등록된 Handler가 없는 API 경로 | 요청한 API Endpoint를 찾을 수 없습니다. |
| `METHOD_NOT_ALLOWED` | 405 | 경로는 존재하지만 HTTP Method를 지원하지 않음 | 허용되지 않은 HTTP Method입니다. |
| `NOT_ACCEPTABLE` | 406 | 요청의 `Accept` 조건을 만족하는 응답을 만들 수 없음 | 요청한 응답 형식을 제공할 수 없습니다. |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 요청 `Content-Type`을 해당 Handler가 지원하지 않음 | 지원하지 않는 요청 형식입니다. |

Spring MVC가 계산한 실제 HTTP Status와 `Allow`, `Accept` 등 프로토콜 헤더는 유지한다. 공통
Envelope를 직렬화할 수 있도록 오류 응답의 `Content-Type`은 `application/json`으로 명시하며,
요청 경로, 내부 Handler 정보와 예외 원문은 노출하지 않는다.

리소스 ID로 직접 식별한 TestSuite, TestCase, TestRun 또는 TestRunResult가 없는 경우에는 기존
`*_NOT_FOUND` Code를 계속 사용한다. `ENDPOINT_NOT_FOUND`는 라우팅 실패에만 사용한다.

OpenAPI가 열거하는 개별 Operation 응답에 네 오류를 반복해서 추가하지 않는다. 모든 API에
공통으로 적용되는 프로토콜 오류 정책으로 API 안내와 오류 계약에 기록한다.

## Alternatives

### Spring `ProblemDetail`을 공식 예외로 유지

HTTP 표준 형식을 그대로 사용할 수 있지만 클라이언트가 두 오류 구조를 파싱해야 하고,
GuardBench의 안정적인 `data.code` 분기 계약이 적용되지 않아 선택하지 않았다.

### 일부 오류만 공통 Envelope로 변환

현재 필요해 보이는 오류만 처리할 수 있지만 어떤 오류가 어느 형식인지에 대한 예외 규칙이
늘어나고 새로운 Spring MVC 오류가 추가될 때 일관성이 다시 깨지므로 선택하지 않았다.

### 네 오류를 `VALIDATION_ERROR`로 변환

오류 Code 수를 늘리지 않지만 405, 406, 415를 400으로 축소해 HTTP 의미를 왜곡하므로 선택하지
않았다.

## Consequences

- 클라이언트는 응답 Body가 있는 GuardBench API 오류를 하나의 Envelope로 파싱할 수 있다.
- 공개 Application Error Code가 네 개 늘어나므로 Java enum, 오류 문서와 회귀 테스트를 함께
  변경해야 한다.
- 404 라우팅 실패와 404 리소스 부재를 서로 다른 안정적인 Code로 구분할 수 있다.
- Status와 프로토콜 헤더를 보존하므로 HTTP 클라이언트의 표준 동작은 유지된다.
- 되돌릴 때는 네 Code와 MVC 변환을 제거하고 Spring `ProblemDetail` 기본 처리를 복원한다. DB와
  데이터 마이그레이션은 없다.

## Validation

- 등록되지 않은 경로가 `404 ENDPOINT_NOT_FOUND` Envelope를 반환한다.
- 존재하는 경로에 지원하지 않는 Method를 보내면 `405 METHOD_NOT_ALLOWED` Envelope와 올바른
  `Allow` 헤더를 반환한다.
- 지원하지 않는 `Accept` 요청이 `406 NOT_ACCEPTABLE` Envelope를 반환한다.
- 지원하지 않는 Content-Type 요청이 `415 UNSUPPORTED_MEDIA_TYPE` Envelope를 반환한다.
- 네 응답의 `httpStatus`가 실제 HTTP Status와 같고 내부 예외 정보가 노출되지 않는다.
- 기존 Validation, Application Error와 예상하지 못한 500 오류 계약 테스트가 계속 통과한다.
