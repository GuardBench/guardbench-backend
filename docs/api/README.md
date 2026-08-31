# GuardBench API V1

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-01
> Canonical source: GitHub
> Origin: [Notion API 명세서](https://app.notion.com/p/3c0eeed6b62d805dac0be8db487b1359)

API의 단일 schema 명세는 [openapi.yaml](openapi.yaml)이다. Endpoint, 필드, Enum, Validation과 응답 예시는 OpenAPI를 우선한다. 이 문서는 구현자가 OpenAPI를 해석할 때 필요한 공통 의미와 현재 구현 상태를 정리한다.

## 계약 층위

OpenAPI는 [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)을 HTTP로 구체화한 목표 공개 계약이다. #114의 Evaluation Profile → EvaluatorReference 고정과 #115/#125/#128의 HTTP Application Target 경계는 구현되었고, #116~#119가 Evaluator orchestration, Quality Gate와 Regression의 남은 차이를 해소한다.

```text
TestCaseSnapshot → OpenAI-compatible AI Application Target → Natural Language Response
                 → Evaluator → EvaluationResult → Assertion → Quality Gate
```

현재 물리 구현은 [TestRun Persistence](../architecture/testrun-persistence.md)와 코드를 함께 확인한다.

## 공통 규칙

- Body가 있는 응답은 `httpStatus`, `message`, `data` envelope를 사용한다.
- 삭제 성공은 `204 No Content`이며 Body가 없다.
- DTO 이름은 `{Domain}{UseCase}{Req|Res}` 형식이다.
- HTTP 오류와 도메인 판정 결과는 다르다. Quality Gate `FAIL`은 정상적인 HTTP 200 조회 결과일 수 있다.
- Pagination은 `page` 1-based, `size` 기본 20/최대 100이다.
- 알 수 없는 요청 필드는 `400 VALIDATION_ERROR`다.
- 필수 문자열은 `null`, 빈 문자열, 공백 문자열을 허용하지 않는다.
- 시각은 UTC ISO 8601로 주고받는다.

## TestSuite와 TestCase

- TestSuite는 TestCase 없이 만들 수 있다.
- 초기 TestCase가 있으면 TestSuite와 한 트랜잭션에서 생성하며 최대 100개다.
- 이름 중복은 허용하고 ID로 식별한다.
- `category`는 고정 Enum이 아닌 비어 있지 않은 문자열이다.
- TestCase 수정/삭제는 과거 TestRun Snapshot과 결과를 변경하지 않는다.

## TestRun 생성 계약

`POST /api/v1/test-runs`는 비동기 실행 요청을 접수하고 `202 Accepted`를 반환한다.

요청 핵심은 다음 세 값이다.

```json
{
  "testSuiteId": 1,
  "target": {
    "type": "HTTP_ENDPOINT",
    "identifier": "https://example.com/v1/chat/completions",
    "model": "example-model",
    "revision": "optional-application-revision"
  },
  "evaluationProfile": {
    "checks": ["PROMPT_INJECTION", "PII_LEAKAGE"],
    "strictness": "STRICT"
  }
}
```

### Application Target

MVP의 `HTTP_ENDPOINT`는 **OpenAI-compatible chat completions 계약만 지원한다.** Generic `{"input": ...}` / `{"response": ...}` HTTP 계약은 지원하지 않는다.

- `target.type`: `HTTP_ENDPOINT` 고정
- `target.identifier`: full HTTP/HTTPS endpoint URL, 필수
- `target.model`: OpenAI-compatible request의 model 값, 필수
- `target.revision`: Application 배포/모델 revision을 재식별하기 위한 선택 문자열

Application 호출:

```http
POST {target.identifier}
Content-Type: application/json
Accept: application/json
```

```json
{
  "model": "example-model",
  "messages": [
    {"role": "user", "content": "<TestCaseSnapshot.input>"}
  ]
}
```

성공 응답은 HTTP 2xx + JSON이며 `choices[0].message.content`가 비어 있지 않은 문자열이어야 한다. 응답의 추가 metadata는 허용한다.

지원하지 않는 범위:

- API Key/Secret/OAuth/custom header 관리
- streaming/SSE
- tool/function calling
- multimodal content
- Responses API
- 임의 JSONPath/custom extractor

HTTP 오류와 timeout, redirect, response size, SSRF/egress 정책은 [HTTP Endpoint Application Target Adapter](../integrations/http-endpoint-target.md)를 따른다.

### Evaluation Profile

- `checks`: `PROMPT_INJECTION | PII_LEAKAGE | HARMFUL_CONTENT`
- `strictness`: `RELAXED | STANDARD | STRICT`
- 사용자는 Evaluator provider/type, AWS Bedrock Guardrail identifier/version을 제출하지 않는다.
- Backend가 profile을 canonical catalog key로 정규화하고 immutable `EvaluatorReference`를 고정한다.
- PII-only는 strictness를 요청 snapshot에는 보존하지만 catalog resolution에서는 collapse한다.
- 지원되는 API 입력 조합은 21개이며 distinct canonical catalog 결과는 19개다.

세부 규칙은 [Evaluation Profile Catalog](../domain/evaluation-profile-catalog.md)를 따른다.

### Idempotency

`Idempotency-Key`는 선택 사항이다. 같은 key + 같은 정규화 요청은 기존 TestRun을 반환하며 다른 요청에 재사용하면 `409 IDEMPOTENCY_KEY_CONFLICT`다. fingerprint에는 Target `model`도 포함한다.

## TestRun 조회와 평가 결과

- 목록과 상세는 실행 중에도 조회할 수 있다.
- `status`: `QUEUED | PREPARING | RUNNING | FINISHED`
- 실행 중 아직 결정되지 않은 `executionOutcome`, `qualityGate`는 `null`이다.
- 상세 응답의 Target에는 요청 당시 `identifier`, nullable `revision`, 필수 `model`이 포함된다.
- 개별 결과 목록은 `FINISHED`에서만 조회한다. 그 전에는 `409 TEST_RUN_NOT_FINISHED`다.
- Application 자연어 response는 Evaluator 내부 입력이며 public 결과 DTO에 노출하지 않는다.
- 실패 단계는 `APPLICATION_TARGET | EVALUATOR`로 구분한다.

현재 Worker는 아직 legacy 결과 저장 경계를 일부 사용하며 #116/#117이 Application response → Evaluator verdict → Assertion 흐름을 완성한다.

## Quality Gate

Quality Gate는 같은 TestRun의 Assertion 결과만 집계한다. 과거 Run이나 Regression 결과를 입력으로 사용하지 않는다. `PASS | FAIL | NOT_EVALUATED`를 사용하며 구체 metrics/threshold는 #118이 소유한다.

## Regression

- comparable Run은 동일한 테스트 정의와 동일한 실제 Evaluator 설정을 사용한 완료 Run이다.
- Application Target/revision은 비교 축이므로 달라도 된다.
- Regression은 저장된 결과만 비교하며 Application/Evaluator를 다시 호출하지 않는다.
- `GET /api/v1/test-runs/{testRunId}/comparable-runs`
- `GET /api/v1/test-runs/{currentRunId}/comparisons/{comparisonRunId}`
- 구체 구현은 #119가 담당한다.

## Frontend 연동 시 주의점

Frontend는 TestRun 생성 시 다음만 제출한다.

```text
TestSuite
+ OpenAI-compatible HTTP Application(endpoint + model)
+ inline Evaluation Profile
```

Evaluator provider, Guardrail identifier/version, EvaluatorReference는 생성 UI에 노출하지 않는다. Frontend DTO와 UI mapping은 OpenAPI의 `TestRunCreateReq`, `TargetReferenceReq`, `EvaluationProfileReq`를 기준으로 한다.
