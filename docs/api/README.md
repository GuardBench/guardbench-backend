# GuardBench API V1

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-31
> Canonical source: GitHub
> Origin: [Notion API 명세서](https://app.notion.com/p/3c0eeed6b62d805dac0be8db487b1359)

API의 단일 명세는 [openapi.yaml](openapi.yaml)이다. Endpoint, 필드, Enum, Validation과 응답 예시는 OpenAPI에서 확인한다. 이 문서는 OpenAPI를 읽고 구현할 때 필요한 공통 해석을 설명한다. Notion과 충돌하면 OpenAPI를 우선하고 차이를 보고한다.

## 계약 층위

OpenAPI는 [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)을 HTTP로 구체화한 **합의된 목표 API 계약**이다. 아직 Java·DB에 모두 구현된 계약은 아니며 #114~#119가 구현 차이를 순차 해소한다.

```text
TestCaseSnapshot → AI Application Target → Natural Language Response
                 → Evaluator → EvaluationResult → Assertion → Quality Gate
```

이번 #113은 공개 request/response와 endpoint 의미를 확정하지만 미래 물리 저장 구조를 추측하지 않는다. 현재 배포 동작이 필요하면 [TestRun Persistence](../architecture/testrun-persistence.md)의 current implementation 경계와 코드를 함께 확인한다.

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
- 초기 TestCase가 있으면 TestSuite와 한 트랜잭션에서 생성하며 최대 100개다.
- 이름 중복은 허용하고 ID로 식별한다. `category`는 고정 Enum이 아닌 비어 있지 않은 문자열이다.
- TestCase는 독립 Aggregate다. 목록은 별도 API로 조회하며 TestSuite 응답에는 전체 배열 대신 `testCaseCount`가 있다.
- TestCase 수정은 과거 TestRun의 Snapshot을 바꾸지 않는다. 삭제는 논리 삭제이며 과거 실행 결과는 유지한다.

### TestRun 접수 — agreed contract

- `POST /api/v1/test-runs`는 비동기 작업을 접수하고 `202 Accepted`를 반환한다.
- `Idempotency-Key`는 선택 사항이다. 키와 정규화된 요청 fingerprint가 같으면 기존 접수 결과를, 다르면 `409 IDEMPOTENCY_KEY_CONFLICT`를 반환한다. 생략하면 요청마다 새 TestRun을 만든다.
- 요청 핵심은 `testSuiteId`, 단일 `HTTP_ENDPOINT` Application `target`, inline `evaluationProfile`이다.
- `evaluationProfile.checks`는 `PROMPT_INJECTION | PII_LEAKAGE | HARMFUL_CONTENT`, `strictness`는 `RELAXED | STANDARD | STRICT`다. 이는 UI 설문·선택으로 정한 평가 목적이며 독립 저장 리소스나 `evaluationProfileId`가 아니다.
- 사용자는 `evaluator.type`, provider, `AWS_BEDROCK`, Guardrail identifier/version을 요청에 제출하지 않는다. GuardBench가 profile을 실제 Evaluator 설정으로 해석하고 실행에 사용한 설정을 내부적으로 고정한다.
- 접수 시 TestSuite의 현재 TestCase를 불변 Snapshot으로 복사한다. 빈 Suite는 `409 TEST_SUITE_EMPTY`다.
- 접수 트랜잭션의 목표 의미는 `QUEUED` TestRun, 요청한 Evaluation Profile, 실행 조건, Snapshot, 선택적인 idempotency 정보와 `TestRunRequested` OutboxEvent를 원자적으로 고정하는 것이다. 구체 물리 저장 모델은 #114가 확정한다. 외부 호출은 commit 뒤 Worker가 수행하며 이후 오류는 접수 HTTP 응답을 바꾸지 않는다.

현재 구현은 아직 `BEDROCK_GUARDRAIL` Target을 요청으로 받고 inline Evaluation Profile을 해석하지 않는다. #114·#115가 목표 요청 계약에 맞게 Java·DB를 전환한다.

### TestRun 조회와 평가 결과 — agreed contract

- 목록과 상세는 실행 중에도 조회할 수 있다. 아직 평가되지 않은 값은 `null`이고 이를 `NOT_EVALUATED`로 바꾸지 않는다.
- 상세는 요청한 Application `target`과 `evaluationProfile`을 다시 확인할 수 있어야 한다. Evaluator/provider 설정은 사용자 요청 필드가 아니며, 공개 응답 metadata는 #114·#116의 실제 고정 모델이 확정될 때 별도로 검토한다.
- 상세의 `qualityGate`는 실행 중 `null`이다. 종료 후 Quality Gate는 같은 TestRun의 Assertion 결과만 집계한다.
- 개별 결과 목록은 `FINISHED`에서만 조회한다. 그 전에는 `409 TEST_RUN_NOT_FINISHED`다.
- 개별 결과는 Snapshot input, Application의 자연어 `targetResponse`, `evaluatorVerdict`, `expectedAction`, `assertionStatus`를 구분한다. 값은 실행 당시 저장 결과이며 현재 TestCase 수정과 무관하다.
- `evaluationOutcome` 필터는 `TRUE_POSITIVE | TRUE_NEGATIVE | FALSE_POSITIVE | FALSE_NEGATIVE` 상세 조회에 사용한다.
- Evaluator metrics의 분류는 다음과 같다.

| ExpectedResult | Evaluator verdict | Evaluation outcome |
| --- | --- | --- |
| BLOCK | BLOCK | TRUE_POSITIVE |
| BLOCK | ALLOW | FALSE_NEGATIVE |
| ALLOW | BLOCK | FALSE_POSITIVE |
| ALLOW | ALLOW | TRUE_NEGATIVE |

현재 구현은 Application response와 Evaluator verdict를 분리하지 않고 `actualAction`으로 공개하며 Quality Gate를 계산하지 않는다. #115~#118이 이 차이를 구현한다.

### Regression — agreed contract

- `GET /api/v1/test-runs/{testRunId}/comparable-runs`는 동일한 테스트 정의와 동일한 실제 Evaluator 설정을 사용한 완료 Run만 반환한다. Application Target/revision은 비교 축이므로 달라도 된다.
- `GET /api/v1/test-runs/{currentRunId}/comparisons/{comparisonRunId}`는 comparison Run에서 current Run으로의 저장 verdict·Assertion 변화만 계산한다.
- Regression 경로는 Application 또는 Evaluator를 다시 호출하지 않는다.
- 비교 불가능한 두 Run의 직접 비교 요청은 `409 TEST_RUNS_NOT_COMPARABLE`이다.

현재 Java에는 이 endpoint가 없으며 #119가 구현한다.

## 구현 경계

- Controller는 Envelope, HTTP 상태, Header와 DTO 매핑을 담당한다. Domain 객체나 JPA Entity를 직접 반환하지 않는다.
- API Enum과 Domain Enum은 문자열 값이 같아도 경계에서 명시적으로 변환한다.
- TestRun 조회 API는 `testrun/presentation`이 소유한다. 다른 Context 값이 필요하면 승인된 소비자 소유 Projection Port를 사용한다.
- OpenAPI 변경은 공개 계약 변경이다. #113에서 승인한 목표 계약을 구현할 때 Java·DB·테스트를 임의로 축소하거나 provider 선택 입력을 추가하지 않는다.

## MVP 이후

로그인·권한, CSV 대량 등록, LLM 기반 생성, Dashboard·시계열 API, WebSocket/SSE, 사용자 정의 Quality Gate와 외부 배포 연동은 현재 계약 밖이다. 추가할 때 기존 Endpoint 의미를 암묵적으로 바꾸지 않고 별도 계약으로 설계한다.
