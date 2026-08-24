# GuardBench API V1

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: [Notion API 명세서](https://app.notion.com/p/3c0eeed6b62d805dac0be8db487b1359)
> AI assistance: 이 문서의 초안은 LLM의 도움으로 작성되었으며 사람의 검토가 필요합니다.

API의 단일 명세는 [openapi.yaml](openapi.yaml)이다. Endpoint, 필드, Enum, Validation, 응답 예시는 OpenAPI에서 확인한다. 이 문서는 OpenAPI를 읽고 구현할 때 필요한 공통 해석만 설명한다. Notion과 충돌하면 OpenAPI를 우선하고 차이를 보고한다.

## 빠른 탐색

| 찾는 내용 | OpenAPI 위치 |
| --- | --- |
| 지원 API | `paths` |
| Query·Path Parameter | `components.parameters` |
| 요청·응답 필드와 Validation | `components.schemas` |
| 공통 오류 | `components.responses`와 `ErrorResponse` |
| Enum 값 | 각 schema의 `enum` |

MVP는 TestSuite·TestCase 관리, TestRun 비동기 실행, TestRun 목록·상세·결과 조회를 제공한다. 인증·인가는 적용하지 않으며 `security: []`를 사용한다.

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

### TestRun 접수

- `POST /api/v1/test-runs`는 비동기 작업을 접수하고 `202 Accepted`를 반환한다.
- `Idempotency-Key`는 선택 사항이다. 키와 정규화된 요청 fingerprint가 같으면 기존 접수 결과를, 다르면 `409 IDEMPOTENCY_KEY_CONFLICT`를 반환한다. 생략하면 요청마다 새 TestRun을 만든다.
- Baseline은 numbered version을 사용한다. Candidate는 numbered version 또는 `DRAFT`를 요청할 수 있으며, `DRAFT`는 Worker가 `PREPARING` 단계에서 numbered version으로 materialize하여 고정한다.
- 접수 시 TestSuite의 현재 TestCase를 불변 Snapshot으로 복사한다. 빈 Suite는 `409 TEST_SUITE_EMPTY`다.
- 접수 트랜잭션은 `QUEUED` TestRun, Snapshot, 선택적인 idempotency 정보와 `TestRunRequested` OutboxEvent를 저장한다. Candidate materialization과 외부 호출은 commit 뒤 Worker가 수행하며, 이후 오류는 접수 HTTP 응답을 바꾸지 않는다.

### TestRun 조회

- 목록과 상세는 실행 중에도 조회할 수 있다. 아직 평가되지 않은 값은 `null`이고 이를 `NOT_EVALUATED`로 바꾸지 않는다.
- 상세의 `qualityGate`는 실행 중 `null`이다. `NOT_EVALUATED`는 실행이 끝났지만 평가할 수 없는 경우의 도메인 결과다.
- 개별 결과 목록은 `FINISHED`에서만 조회한다. 그 전에는 `409 TEST_RUN_NOT_FINISHED`다.
- Snapshot 입력과 기대값, 실행 결과, 평가 결과는 실행 당시 값이며 현재 TestCase 수정과 무관하다.
- Baseline이 없는 실행은 비교 결과가 없으므로 `change`가 `null`이다. 실행 실패나 비교 불가의 nullable 조합은 OpenAPI schema 설명과 [평가 계약](../domain/evaluation-contract.md)을 따른다.

## 구현 경계

- Controller는 Envelope, HTTP 상태, Header와 DTO 매핑을 담당한다. Domain 객체나 JPA Entity를 직접 반환하지 않는다.
- API Enum과 Domain Enum은 문자열 값이 같아도 경계에서 명시적으로 변환한다.
- TestRun 조회 API는 `testrun/presentation`이 소유한다. Evaluation 값이 필요하면 승인된 소비자 소유 Projection Port를 사용한다.
- OpenAPI 변경은 공개 계약 변경이다. 구현 편의를 위해 먼저 바꾸지 말고 Issue의 승인 경계를 따른다.

## MVP 이후

로그인·권한, CSV 대량 등록, LLM 기반 생성, Dashboard·시계열 API, WebSocket/SSE, 사용자 정의 Quality Gate와 외부 배포 연동은 현재 계약 밖이다. 추가할 때 기존 Endpoint 의미를 암묵적으로 바꾸지 않고 별도 계약으로 설계한다.
