# GuardBench API V1

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Origin: [Notion API 명세서](https://app.notion.com/p/3c0eeed6b62d805dac0be8db487b1359)

API의 단일 명세는 [openapi.yaml](openapi.yaml)이다. Endpoint, 필드, Enum, Validation과 응답 예시는 OpenAPI에서 확인한다. 이 문서는 OpenAPI를 읽고 구현할 때 필요한 공통 해석을 설명한다. Notion과 충돌하면 OpenAPI를 우선하고 차이를 보고한다.

## 계약 층위

OpenAPI는 [ADR 0013](../decisions/0013-response-behavior-classifier.md)을 HTTP로 구체화한 **합의된 API 계약**이다. #115/#125/#128의 OpenAI-compatible HTTP Application Target 경계, #173의 Response Behavior Classifier 전환, #117의 Worker orchestration, #118의 현재 Run Quality Gate 집계와 #119의 stored-result Regression API가 구현되었다.

```text
TestCaseSnapshot → OpenAI-compatible AI Application Target → Natural Language Response
                 → Evaluator → EvaluationResult → Assertion → Quality Gate
```

현재 배포 동작이 필요하면 [TestRun Persistence](../architecture/testrun-persistence.md)의 current implementation 경계와 코드를 함께 확인한다.

## 빠른 탐색

| 찾는 내용 | OpenAPI 위치 |
| --- | --- |
| 지원 API | `paths` |
| Query·Path Parameter | `components.parameters` |
| 요청·응답 필드와 Validation | `components.schemas` |
| 공통 오류 | `components.responses`와 `ErrorResponse` |
| Enum 값 | 각 schema의 `enum` |

목표 API는 TestSuite·TestCase 관리, TestRun 비동기 실행, TestRun 목록·상세·결과 조회, Evaluator metrics와 저장 결과 기반 Regression 조회를 제공한다. 인증·인가는 적용하지 않으며 `security: []`를 사용한다.

## 공통 규칙

### 응답과 오류

- Body가 있는 응답은 `httpStatus`, `message`, `data` envelope를 사용한다.
- 삭제 성공은 `204 No Content`이며 Body가 없다.
- DTO 이름은 `{Domain}{UseCase}{Req|Res}` 형식이다.
- HTTP 오류와 도메인 판정 결과는 다르다. Quality Gate가 `FAIL`이어도 조회가 성공하면 HTTP 200이다.
- Application Error 목록과 우선순위는 [애플리케이션 오류](../conventions/application-errors.md)를 따른다.
- 등록되지 않은 경로의 404와 Spring MVC의 405, 406, 415도 공통 Error Envelope를 사용한다.
  오류 응답의 `Content-Type`은 `application/json`이고, Status와 `Allow`, `Accept` 등 프로토콜 헤더는 유지한다. 구체 Code는
  [ADR 0014](../decisions/0014-http-standard-error-envelope.md)를 따른다.

### 목록 조회

- Pagination은 `page`(1부터 시작, 기본 1)와 `size`(기본 20, 최대 100)를 사용한다. 범위를 벗어나면 자동 보정하지 않고 `400 VALIDATION_ERROR`를 반환한다.
- 범위를 넘은 유효한 페이지는 `200 OK`, 빈 `items`, 실제 집계값을 반환한다. 결과가 0건이면 `totalPages`도 0이다.
- 필터를 먼저 적용하고 전체 결과를 정렬한 뒤 Pagination한다. 서로 다른 필터는 AND, 반복 가능한 같은 필터 값은 OpenAPI에 명시된 경우 OR로 결합한다.
- 다중 정렬은 `sort={field},{asc|desc}`를 반복한다. 허용 필드, 기본 순서와 안정 정렬용 `id` 방향은 endpoint별 OpenAPI 설명을 따른다.
- `Pageable`, `Page`, `Sort` 같은 Spring 타입은 Presentation 또는 Infrastructure 안에서만 사용한다. Application과 Domain에는 프로젝트 자체 조회 모델을 전달한다.

### 식별자와 시간

- Path ID는 양의 정수다. 잘못된 형식·0·음수는 `400 VALIDATION_ERROR`, 유효하지만 없는 ID는 해당 `*_NOT_FOUND` 404다.
- 시각은 UTC 기준 ISO 8601로 주고받는다. 범위 조회의 하한은 포함, 상한은 미포함이다.

### 쓰기 요청

- 알 수 없는 요청 필드는 `400 VALIDATION_ERROR`다.
- 필수 문자열은 `null`, 빈 문자열, 공백 문자열을 허용하지 않는다.
- PATCH에서 생략은 기존 값 유지다. 빈 객체는 허용하지 않는다. 명시적 `null`은 OpenAPI가 허용한 필드에서만 값 제거를 뜻한다.
- 하나의 요청에 포함된 변경은 먼저 전체 검증한 뒤 원자적으로 반영한다. 부분 성공은 허용하지 않는다.

## 도메인별 주의점

### TestSuite와 TestCase

- TestSuite는 TestCase 없이 만들 수 있다. 생성 요청의 `testCases` 생략·`null`·빈 배열은 빈 컬렉션으로 정규화한다.
- 초기 TestCase가 있으면 TestSuite와 한 트랜잭션에서 생성하며 최대 1000개다.
- 이름 중복은 허용하고 ID로 식별한다. `category`는 고정 Enum이 아닌 비어 있지 않은 문자열이다.
- TestCase는 독립 Aggregate다. 목록은 별도 API로 조회하며 TestSuite 응답에는 전체 배열 대신 `testCaseCount`가 있다.
- 기존 Suite의 일괄 등록은 `POST /api/v1/test-suites/{suiteId}/test-cases/bulk`로 1~1000개를 원자적으로 추가한다. 필수 `Idempotency-Key`는 3시간 동안 같은 Suite·항목·순서의 재전송에 최초 생성 ID와 완료 당시 전체 개수를 재사용하며, 다른 요청에 재사용하면 `409 IDEMPOTENCY_KEY_CONFLICT`다.
- TestCase 수정은 과거 TestRun의 Snapshot을 바꾸지 않는다. 삭제는 영구 삭제이며 과거 실행 결과는 유지한다.

### TestRun 접수 — agreed contract

- `POST /api/v1/test-runs`는 비동기 작업을 접수하고 `202 Accepted`를 반환한다.
- TestRun 생성 요청의 선택적 `qualityGatePolicy`로 Assertion 통과율과 실행 성공률의 threshold를 각각 지정한다. 생략하면 기존 기본값인 0.95를 두 지표에 적용하며 생성된 Run에 정책 스냅샷을 고정한다.
- `Idempotency-Key`는 선택 사항이다. 키와 정규화된 요청 fingerprint가 같으면 기존 접수 결과를, 다르면 `409 IDEMPOTENCY_KEY_CONFLICT`를 반환한다. 생략하면 요청마다 새 TestRun을 만든다.
- 요청 핵심은 `testSuiteId`와 단일 `HTTP_ENDPOINT` Application `target`이다. Classifier 설정은 서버 deployment configuration이 소유한다.
- `target.identifier`는 OpenAI-compatible chat completions를 호출할 full HTTP/HTTPS URL이고 `target.model`은 필수다. `target.revision`만 선택 값이다.
- 사용자는 classifier provider, endpoint name, model 또는 prompt를 요청에 제출하지 않는다.
- 접수 시 TestSuite의 현재 TestCase를 불변 Snapshot으로 복사한다. 빈 Suite는 `409 TEST_SUITE_EMPTY`다.
- 접수 트랜잭션은 `QUEUED` TestRun, HTTP Target의 identifier/model/revision, classifier contract version, Snapshot, 선택적인 idempotency 정보와 `TestRunRequested` OutboxEvent를 원자적으로 고정한다. 외부 호출은 commit 뒤 Worker가 수행하며 이후 오류는 접수 HTTP 응답을 바꾸지 않는다.

### HTTP Application Target MVP 실행 계약 — #115, #125, #128

`HTTP_ENDPOINT` Target은 Worker가 Snapshot input마다 OpenAI-compatible chat completions endpoint로 `POST` 요청을 보내고 자연어 응답을 수집하는 실제 SUT다. MVP에서는 generic `{"input": ...}` / `{"response": ...}` HTTP 계약을 지원하지 않는다.

```http
POST {target.identifier}
Content-Type: application/json
Accept: application/json
```

```json
{
  "model": "<target.model>",
  "messages": [{"role": "user", "content": "<TestCaseSnapshot.input>"}]
}
```

성공 응답은 HTTP `2xx`, `Content-Type: application/json`이어야 하며 `choices[0].message.content`의 비어 있지 않은 문자열을 자연어 Application response로 정규화한다. 응답 object의 다른 metadata는 허용한다. streaming/SSE, tool/function calling과 multimodal content는 지원하지 않는다.

실행 오류는 `TargetFailureCode`로 안전하게 수렴한다: `404 → TARGET_NOT_FOUND`, `401/403 → TARGET_ACCESS_DENIED`, 그 밖의 `4xx → TARGET_CONFIGURATION_INVALID`, `5xx → PROVIDER_UNAVAILABLE`, timeout → `PROVIDER_TIMEOUT`, redirect·Content-Type/JSON/shape 위반 → `PROVIDER_RESPONSE_INVALID`.

Target 실행 Adapter는 호출 내부 retry를 수행하지 않는다. 기존 Worker의 execution claim 재전달·최대 3회 시도 경계를 사용해 at-least-once 특성은 유지하되 한 메시지 수신당 Application 호출은 한 번으로 제한한다. 오류 메시지와 로그에는 응답 본문, 입력, URL, 인증 정보와 Provider 원문을 포함하지 않는다.

Target URL은 HTTP/HTTPS absolute URL, host 필수, userinfo·fragment 금지다. Worker 기본 egress 정책은 loopback/private/link-local/multicast 주소를 차단하며, 내부 SUT가 필요한 배포는 `guardbench.http-endpoint.allowed-private-hostnames`에 승인된 hostname을 정확히 지정한다. allowlist를 사용해도 loopback/link-local/multicast/AWS metadata endpoint와 private IP literal은 차단한다. 운영 성능 환경에서 `guardbench.http-endpoint.allow-private-addresses=true`를 전역 설정하지 않는다. 응답 본문은 1 MiB를 넘을 수 없다.

HTTP Application Target 실행, OpenAI-compatible response 정규화, SageMaker Response Behavior Classifier와 Application response → classifier verdict → Assertion Worker orchestration, 현재 Run Quality Gate 및 stored-result Regression 결과 API는 구현되었다.

### TestRun 조회와 평가 결과 — agreed contract

- 목록과 상세는 실행 중에도 조회할 수 있다. 아직 평가되지 않은 값은 `null`이고 이를 `NOT_EVALUATED`로 바꾸지 않는다.
- 상세는 요청한 Application `target`과 classifier contract version을 다시 확인할 수 있어야 한다. HTTP Target의 `model`은 항상 존재하며 `revision`은 요청에서 생략했으면 null이다. Classifier provider 내부 식별자는 사용자 입력 필드가 아니다.
- 상세의 `qualityGate`는 실행 중 `null`이다. 종료 후 Quality Gate는 같은 TestRun의 Assertion 결과만 집계한다.
- 개별 결과 목록은 `FINISHED`에서만 조회한다. 그 전에는 `409 TEST_RUN_NOT_FINISHED`다.
- 개별 결과의 `TestRunResultItemRes`는 Snapshot input, `executionStatus`, `evaluatorVerdict`, `expectedAction`, `assertionStatus`와 안전한 `error`를 제공한다. 값은 실행 당시 저장 결과이며 현재 TestCase 수정과 무관하다.
- 개별 결과 목록 `TestRunResultItemRes`에는 Application의 자연어 응답을 포함하지 않으며, `GET /api/v1/test-runs/{testRunId}/results/{testCaseSnapshotId}`의 `TestRunResultDetailRes`에서만 저장된 `applicationResponse`를 그대로 반환한다. 응답이 생성되지 않은 결과는 `null`이다.
- `error.stage`는 `APPLICATION_TARGET | EVALUATOR`로 실패 단계를 구분한다. code의 구체 taxonomy는 #117이 소유하며 provider 원문, stack trace, credential과 ARN은 노출하지 않는다.
- `evaluationOutcome` 필터는 `TRUE_POSITIVE | TRUE_NEGATIVE | FALSE_POSITIVE | FALSE_NEGATIVE` 상세 조회에 사용한다.
- `attentionType`은 `FALSE_NEGATIVE | FALSE_POSITIVE | EXECUTION_FAILED | TIMED_OUT | NOT_STARTED`이며,
  반복 값은 OR, 다른 필터와는 AND로 결합한다. 결과 항목에는 처리 미완료를 우선한 대표 유형 하나만 반환한다.
- `includeFacets=attention`이면 선택한 `attentionType`은 제외하고 나머지 일반 필터를 반영한 전체 결과 수와
  유형별 집계를 반환한다. 따라서 사용자가 일부 chip을 선택해도 선택하지 않은 chip의 집계가 유지된다.
- Attention 필터에 명시 정렬이 없으면 `severity DESC` → `FALSE_NEGATIVE`, `EXECUTION_FAILED`,
  `TIMED_OUT`, `FALSE_POSITIVE`, `NOT_STARTED` → `snapshotId ASC` 순으로 정렬한 뒤 페이지를 적용한다.
- Evaluator metrics의 분류는 다음과 같다.

| ExpectedResult | Evaluator verdict | Evaluation outcome |
| --- | --- | --- |
| BLOCK | BLOCK | TRUE_POSITIVE |
| BLOCK | ALLOW | FALSE_NEGATIVE |
| ALLOW | BLOCK | FALSE_POSITIVE |
| ALLOW | ALLOW | TRUE_NEGATIVE |

현재 구현은 Application response를 `TestExecution`에 실행 당시 증거로 보존하고, paginated 결과 목록에서는 Evaluator verdict와 Assertion을 분리해 제공한다. `GET /api/v1/test-runs/{testRunId}/results/{testCaseSnapshotId}`의 상세 결과에서만 저장된 Application response 원문을 반환하며, 같은 TestRun의 Assertion과 실행 성공률로 Quality Gate를 계산한다.

`QualityGateRes`는 `status`와 nullable `metrics`를 제공한다. `metrics.assertion`과 `metrics.execution`은 각각 판정 당시의 `value`, `threshold`, backend 판정 결과인 `passed`를 포함한다. 두 지표가 해당 TestRun의 threshold 이상이면 `PASS`, 하나라도 기준 미만이면 `FAIL`이다. 평가 가능한 Assertion이 하나도 없으면 존재하지 않는 근거를 0으로 만들지 않고 `NOT_EVALUATED`와 `metrics: null`을 반환한다. Assertion 실패와 실행 실패는 서로 다른 분모로 집계하며, Regression 또는 과거 Run 결과를 Quality Gate에 넣지 않는다.

기존 공개 소비처의 순차 배포를 위해 `metrics.assertionPassRate`와 `metrics.executionSuccessRate`도 각각 구조화된 metric의 `value`와 같은 값으로 임시 유지한다. 두 필드는 deprecated이며 소비처가 구조화된 근거로 전환된 뒤 별도 계약 변경에서 제거한다.

### Regression — agreed contract

- `GET /api/v1/test-runs/{testRunId}/comparable-runs`는 동일한 테스트 정의와 동일한 실제 Evaluator 설정을 사용한 완료 Run만 반환한다. Application Target/revision은 비교 축이므로 달라도 된다.
- `GET /api/v1/test-runs/{currentRunId}/comparisons/{comparisonRunId}`는 comparison Run에서 current Run으로의 저장 verdict·Assertion 변화만 계산한다.
- `GET /api/v1/test-runs/{currentRunId}/comparisons/{comparisonRunId}/summary`는 같은 집계에서 case-level `items`를 제외해 요약 화면의 응답 크기를 일정하게 유지한다.
- Regression 경로는 Application 또는 Evaluator를 다시 호출하지 않는다.
- 비교 불가능한 두 Run의 직접 비교 요청은 `409 TEST_RUNS_NOT_COMPARABLE`이다.
- #113은 endpoint와 Run 식별자까지 고정하며 구체 comparability key와 Regression 상세 response DTO는 #119가 최종 소유한다.

Regression API는 #119에서 구현되었다. 비교 가능성은 TestCaseSnapshot의 전체 정의
`sourceTestCaseId/name/input/expectedAction/severity/category`와 Evaluator provider 설정
`type/identifier/fixed revision`이 모두 같은지로 판단한다. EvaluatorReference의 생성별 UUID 자체는
비교 키로 사용하지 않는다.

변화 방향은 `comparisonRun → currentRun`이다. 두 verdict가 같으면 `NO_CHANGE`이고, ExpectedResult가
`BLOCK`일 때 current가 `ALLOW`면 `SECURITY_REGRESSION`, ExpectedResult가 `ALLOW`일 때 current가
`BLOCK`이면 `USABILITY_REGRESSION`이다. 반대 방향은 `IMPROVEMENT`이며, 저장 verdict가 없는 케이스는
`NOT_COMPARABLE`로 집계한다. 목록과 직접 비교 모두 완료된 Run만 대상으로 하며 외부 Target/Evaluator를 호출하지 않는다.

## Frontend 계약 라우팅

| Frontend Issue | 참조할 backend 공개 계약 | 구현 시 전제 |
| --- | --- | --- |
| #27 TestRun 생성 | `POST /api/v1/test-runs`, `TestRunCreateReq`, `TargetReferenceReq` | OpenAI-compatible HTTP Application endpoint와 필수 model만 제출하며 classifier 설정 입력은 없다. |
| #28 실행 상세·결과 | `GET /api/v1/test-runs/{testRunId}`, `TestRunDetailRes`, `GET .../results`, `TestRunResultItemRes`, `GET .../results/{testCaseSnapshotId}`, `TestRunResultDetailRes`, `QualityGateRes` | 목록은 classifier verdict, ExpectedResult, Assertion, 실행 상태와 안전한 오류를 표시하고, 개별 상세에서 저장된 ApplicationResponse를 표시한다. |
| #29 FN/FP 분석 | 결과의 `evaluationOutcome`과 `GET .../evaluator-metrics`의 `EvaluatorMetricsRes` | backend 분류를 source of truth로 사용한다. |
| #30 Regression | `GET .../comparable-runs`, `GET .../comparisons/{comparisonRunId}` | backend가 반환한 comparable Run만 사용한다. provider 선택 UI나 재실행 흐름을 만들지 않고 저장된 비교 결과 DTO를 사용한다. |

## 구현 경계

- Controller는 Envelope, HTTP 상태, Header와 DTO 매핑을 담당한다. Domain 객체나 JPA Entity를 직접 반환하지 않는다.
- API Enum과 Domain Enum은 문자열 값이 같아도 경계에서 명시적으로 변환한다.
- TestRun 조회 API는 `testrun/presentation`이 소유한다. 다른 Context 값이 필요하면 승인된 소비자 소유 Projection Port를 사용한다.
- OpenAPI 변경은 공개 계약 변경이다. #113에서 승인한 목표 계약을 구현할 때 Java·DB·테스트를 임의로 축소하거나 provider 선택 입력을 추가하지 않는다.

## MVP 이후

로그인·권한, CSV 대량 등록, LLM 기반 생성, Dashboard·시계열 API, WebSocket/SSE, threshold 외의 사용자 정의 Quality Gate 공식과 외부 배포 연동은 현재 계약 밖이다. 추가할 때 기존 Endpoint 의미를 암묵적으로 바꾸지 않고 별도 계약으로 설계한다.
