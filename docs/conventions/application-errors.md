# 애플리케이션 오류 코드

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-31
> Canonical source: GitHub
> Origin: [Notion 애플리케이션 오류 코드](https://app.notion.com/p/3c1eeed6b62d81d3a7c9f014bb788aa8)

코드는 `UPPER_SNAKE_CASE`와 `{RESOURCE}_{REASON}` 형식을 우선한다. 클라이언트는 `message`가 아니라 `data.code`를 사용한다. 아래 목록은 MVP Application Error의 전체 계약이며 새 Code 추가나 기존 의미 변경은 공개 계약 변경으로 다룬다.

## 공통 오류 응답

Validation을 제외한 오류는 다음 구조를 사용한다.

```json
{
  "httpStatus": 404,
  "message": "TestRun을 찾을 수 없습니다.",
  "data": {
    "code": "TEST_RUN_NOT_FOUND"
  }
}
```

- `httpStatus`는 실제 HTTP Response Status와 같아야 한다.
- `data.code`는 클라이언트 분기와 자동화 테스트에서 사용하는 안정적인 값이다.
- `message`는 사용자 안내용이며 클라이언트 로직에서 문자열을 비교하지 않는다.
- 내부 예외, SQL, Provider 응답 원문, Stack Trace와 비밀정보를 응답에 노출하지 않는다.

## MVP Application Error

| Code | HTTP | 발생 조건 |
| --- | ---: | --- |
| `VALIDATION_ERROR` | 400 | Path·Query·Header·Body의 형식이나 값이 잘못됨 |
| `TEST_SUITE_NOT_FOUND` | 404 | 유효한 양의 ID에 해당하는 TestSuite가 없음 |
| `TEST_CASE_NOT_FOUND` | 404 | TestCase가 없거나 논리 삭제됨 |
| `TEST_RUN_NOT_FOUND` | 404 | 유효한 양의 ID에 해당하는 TestRun이 없음 |
| `TEST_SUITE_EMPTY` | 409 | TestRun 생성 시 활성 TestCase가 없음 |
| `EVALUATION_PROFILE_NOT_SUPPORTED` | 409 | 운영 Evaluator catalog에 요청한 canonical Evaluation Profile이 없음 |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | 같은 Idempotency-Key를 다른 TestRun 요청에 재사용 |
| `TEST_RUN_NOT_FINISHED` | 409 | 종료가 필요한 결과·지표·비교 API에 FINISHED가 아닌 TestRun 사용 |
| `TEST_RUNS_NOT_COMPARABLE` | 409 | 테스트 정의 또는 실제 Evaluator 설정이 다른 두 TestRun 비교 |
| `INTERNAL_SERVER_ERROR` | 500 | 예상하지 못한 동기 서버 처리 실패 |

기본 `message`는 다음을 권장한다. 문구를 개선할 수는 있지만 Code의 의미는 변경하지 않는다.

| Code | 기본 message |
| --- | --- |
| `VALIDATION_ERROR` | 요청 값이 올바르지 않습니다. |
| `TEST_SUITE_NOT_FOUND` | TestSuite를 찾을 수 없습니다. |
| `TEST_CASE_NOT_FOUND` | TestCase를 찾을 수 없습니다. |
| `TEST_RUN_NOT_FOUND` | TestRun을 찾을 수 없습니다. |
| `TEST_SUITE_EMPTY` | 실행 가능한 TestCase가 없습니다. |
| `EVALUATION_PROFILE_NOT_SUPPORTED` | 지원하는 Evaluator 설정을 찾을 수 없습니다. |
| `IDEMPOTENCY_KEY_CONFLICT` | Idempotency-Key가 다른 요청에 이미 사용되었습니다. |
| `TEST_RUN_NOT_FINISHED` | TestRun이 아직 종료되지 않았습니다. |
| `TEST_RUNS_NOT_COMPARABLE` | 두 TestRun은 비교할 수 없습니다. |
| `INTERNAL_SERVER_ERROR` | 서버 내부 오류가 발생했습니다. |

## Validation Error

```json
{
  "httpStatus": 400,
  "message": "요청 값이 올바르지 않습니다.",
  "data": {
    "code": "VALIDATION_ERROR",
    "errors": [
      {
        "field": "testCases[0].name",
        "message": "이름은 필수입니다."
      },
      {
        "field": "sort[1]",
        "message": "허용되지 않은 정렬 조건입니다."
      }
    ]
  }
}
```

다음은 `VALIDATION_ERROR`다.

- 필수 필드 누락
- 계약에서 금지한 `null`, 빈 문자열 또는 공백 문자열
- 허용되지 않은 Enum
- 알 수 없는 Request Body 필드
- 문자열, 0, 음수 등 유효하지 않은 Path ID
- 잘못된 날짜 형식 또는 역전된 범위
- Pagination 범위 위반
- 허용되지 않은 정렬 필드 또는 방향
- 빈 PATCH 객체
- 비어 있거나 최대 길이를 초과한 `Idempotency-Key`

Field 경로는 외부 API 이름을 사용한다.

- 중첩 필드: `target.identifier`
- 배열 요소: `testCases[0].name`
- Path 또는 Query: `testRunId`, `page`
- 반복 Query: `sort[1]`
- Header: `Idempotency-Key`
- 특정 필드에 귀속할 수 없는 요청 전체 오류: `_request`

한 필드에 여러 오류가 발생할 수 있으므로 Map이 아닌 `errors[]`를 사용한다. 요청만으로 함께 확인할 수 있는 Validation 오류는 한 응답에 함께 반환한다.

## 404 Not Found

### TEST_SUITE_NOT_FOUND

TestSuite 상세·수정, TestCase 목록·생성의 부모 TestSuite, 새로운 TestRun의 대상 TestSuite처럼 Path 또는 Body로 직접 식별한 TestSuite가 없을 때 사용한다.

TestRun 목록의 `testSuiteId`는 검색 Filter다. 유효한 양의 ID지만 해당 TestSuite가 없으면 `404`가 아니라 `200 OK`와 빈 `items`를 반환한다.

### TEST_CASE_NOT_FOUND

TestCase 상세·수정·삭제 대상이 없거나 논리 삭제되었을 때 사용한다. 같은 TestCase를 다시 삭제하는 경우도 같다. 리소스가 원래 없었는지 논리 삭제되었는지는 외부에 구분해서 노출하지 않는다.

### TEST_RUN_NOT_FOUND

TestRun 상세 또는 개별 결과 조회의 양의 ID가 존재하지 않을 때 사용한다. 빈 목록과 범위를 초과한 페이지는 `404`가 아니라 `200 OK`와 빈 `items`를 반환한다.

## 409 Conflict

### TEST_SUITE_EMPTY

TestSuite는 존재하지만 활성 TestCase가 0개여서 TestRun을 생성할 수 없을 때 사용한다. 논리 삭제된 TestCase는 활성 개수에 포함하지 않는다.

### EVALUATION_PROFILE_NOT_SUPPORTED

TestRun 생성 요청의 `checks`와 `strictness`를 canonicalize했을 때 운영자가 사전 provisioning한
Evaluator catalog entry가 없으면 사용한다. PII-only profile은 strictness를 collapse한 canonical key로
조회한다. 요청에 provider 또는 Guardrail identifier/version을 직접 추가해 해결할 수 있는 오류가 아니다.

### IDEMPOTENCY_KEY_CONFLICT

- 같은 Key와 같은 요청: 새 TestRun을 만들지 않고 기존 TestRun을 `202 Accepted`로 반환한다.
- 같은 Key와 다른 요청: `409 IDEMPOTENCY_KEY_CONFLICT`로 응답한다.
- Key 생략: 요청할 때마다 새로운 TestRun을 생성한다.

같은 요청의 재전송이면 현재 TestSuite가 이후 변경되었더라도 기존에 접수된 TestRun을 그대로 반환한다.

### TEST_RUN_NOT_FINISHED

`QUEUED`, `PREPARING`, `RUNNING` 상태에서 개별 결과, Evaluator metrics, comparable runs 또는 comparison을 요청하면 사용한다. 같은 상태에서도 Polling용 TestRun 상태·요약 조회는 `200 OK`다.

### TEST_RUNS_NOT_COMPARABLE

두 Run이 모두 FINISHED지만 테스트 정의 또는 실제 고정 Evaluator 설정이 다를 때 직접 comparison 요청에 사용한다. 같은 `testSuiteId`만으로 비교 가능하다고 판단하지 않는다. comparable runs 목록에서는 이 Run을 제외한다.

## INTERNAL_SERVER_ERROR

DB 연결·저장 실패, TestRun·Snapshot·OutboxEvent 접수 트랜잭션 실패와 같이 요청 계약을 완료하지 못한 예상 밖의 동기 서버 오류에 사용한다.

```json
{
  "httpStatus": 500,
  "message": "서버 내부 오류가 발생했습니다.",
  "data": {
    "code": "INTERNAL_SERVER_ERROR"
  }
}
```

TestRun이 이미 `202 Accepted`로 접수된 뒤 발생한 Application 실행, Evaluator 준비·호출 실패와 timeout은 기존 HTTP 요청의 `500`으로 바꾸지 않고 비동기 TestRun 실행 결과에 기록한다.

## 오류 판단 우선순위

일반 요청은 다음 순서로 판단한다.

1. 요청 형식과 Validation → `400`
2. 직접 식별한 리소스 존재 여부 → `404`
3. 현재 상태에서 수행 가능한지 → `409`
4. 예상하지 못한 서버 실패 → `500`

TestRun 재전송은 Validation 후 Idempotency 기록을 먼저 확인한다.

1. 같은 Key와 같은 요청이면 기존 TestRun 반환
2. 같은 Key와 다른 요청이면 `409`
3. 새로운 요청이면 TestSuite 존재 여부와 활성 TestCase를 검사

## Application Error가 아닌 상태

다음은 HTTP Application Error가 아니다.

- Assertion FAIL
- Regression의 improved, regressed 또는 unchanged 결과
- Quality Gate FAIL
- 비동기 TestExecution 실패와 timeout
- 빈 목록 또는 페이지 범위 초과
- 중복 TestSuite·TestCase 이름
- 인증·인가 오류: MVP에서는 인증·인가를 적용하지 않음

`TestExecution.error.code`는 위 Application Error와 별도 체계다. Provider 호출·timeout·정규화 실패와 같은 실행 오류 Code는 아래 섹션에서 정의한다.

## TestExecution 실행 오류 Code

> Current implementation contract: [ADR 0010: 단일 Target 실행 모델](../decisions/0010-single-target-test-run-model.md)
> Target architecture: [ADR 0011: AI Application Target과 Guardrail Evaluator](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)

이 섹션은 current implementation의 공개 가능한 TestExecution 오류 code를 나열한다. retry·timeout의 운영 근거는 ADR 0005의 대체되지 않은 부분을 참고하며, 이 표는 API·Adapter 구현이 참조할 확정된 code 목록이다.

`TestExecution.error`는 HTTP Application Error와 다른 계층이며 위의 공통 오류 응답 구조를 따르지 않는다. current implementation은 개별 결과의 `execution.error`에 `{ code, message }`를 노출한다. 목표 OpenAPI의 평탄한 `TestRunResultItemRes.error`는 `{ stage, code, message }`로 Application Target과 Evaluator 실패 단계를 구분한다.

| Code | Terminal 상태 | 발생 조건 |
| --- | --- | --- |
| `TARGET_NOT_FOUND` | `FAILED` | 실행 대상 Guardrail/버전을 Provider가 찾지 못함 |
| `TARGET_ACCESS_DENIED` | `FAILED` | Provider가 대상에 대한 접근을 거부함 |
| `TARGET_CONFIGURATION_INVALID` | `FAILED` | 대상 Guardrail 설정이 유효하지 않음 |
| `PROVIDER_UNAVAILABLE` | `FAILED` | Provider 호출이 일시적으로 불가능함 (최대 재시도 소진 후) |
| `PROVIDER_RESPONSE_INVALID` | `FAILED` | Provider 응답을 안전하게 정규화할 수 없음 |
| `PROVIDER_TIMEOUT` | `TIMED_OUT` | Provider 호출이 전체 timeout(15초, ADR 0005) 안에 끝나지 않음 |

이 6개 code는 `com.guardbench.testrun.domain.TestExecutionErrorCode`(Domain enum)와 `com.guardbench.testrun.application.port.out.TargetFailureCode`(소비자 소유 Port enum)에 각각 정의되어 있으며, `PROVIDER_UNAVAILABLE`과 `PROVIDER_TIMEOUT`만 재시도 가능하다(ADR 0005의 대체되지 않은 retry 계약 참고).

- 각 code는 고정된 안전한 message를 사용하며 Provider 원문, SDK 예외 메시지, stack trace, ARN, 자격 증명, 내부 endpoint를 노출하지 않는다.
- 현재 Bedrock 예외 → `TargetFailureCode` 매핑은 [Bedrock Guardrail Adapter](../integrations/bedrock-guardrails-adapter.md)가 소유하고, `TargetFailureCode` → `TestExecutionErrorCode`·terminal 저장은 Worker Application Service가 소유한다.
- 이 표에 없는 code를 추가하거나 기존 code의 terminal 상태·의미를 바꾸는 것은 공개 API 계약 변경이며 별도 ADR 또는 Issue 승인이 필요하다.

목표 구조의 공개 shape은 `error.stage = APPLICATION_TARGET | EVALUATOR`로 실패 경계를 구분한다. 구체 code와 terminal 상태 매핑은 #115~#117이 확정하며, 이 current implementation의 6개 code 표를 미래 계약으로 확장 해석하지 않는다.
