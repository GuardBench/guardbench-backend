# Bedrock Guardrail Evaluator Adapter 설계 근거

> Status: DEPRECATED
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Related: [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)
> Related Issue: #116
> Superseded by: [SageMaker Response Behavior Classifier Adapter 설계 근거](sagemaker-classifier-adapter.md), [ADR 0013](../decisions/0013-response-behavior-classifier.md)

이 문서가 정의하던 `ApplyGuardrail` 기반 Bedrock Guardrail Evaluator Adapter는 [Issue #173](https://github.com/GuardBench/guardbench-backend/issues/173)에서 Response Behavior Classifier Adapter로 대체되었다. `BedrockGuardrailEvaluatorAdapter`, `bedrock_guardrail_evaluator` 테이블과 Guardrail catalog 개념은 코드에서 제거되었다.

현재 계약은 [SageMaker Response Behavior Classifier Adapter 설계 근거](sagemaker-classifier-adapter.md)를 따른다. 이 문서는 과거 결정의 history 보존을 위해서만 남긴다.

---

## 목표 역할 (역사적 기록)

AWS Bedrock Guardrail은 AI Application Target이 아니라 첫 번째 Guardrail Evaluator 구현이다. AI Application이 반환한 자연어 ApplicationResponse를 평가하고 GuardBench 공통 `EvaluationResult(ALLOW | BLOCK)`로 정규화한다.

```text
AI Application Target
        ↓ Natural Language ApplicationResponse
Bedrock Guardrail Evaluator Adapter
        ↓ ApplyGuardrail
EvaluationResult(ALLOW | BLOCK)
```

사용자는 inline Evaluation Profile만 제출한다. GuardBench가 이를 AWS Bedrock Guardrail Evaluator 설정으로 해석하며, Evaluator Adapter만 AWS SDK 타입, Guardrail identifier/version과 provider 응답을 안다. Core에는 소비자가 소유한 scalar/value 계약만 전달하고 TestRun은 실제 사용한 Evaluator 설정과 버전을 불변하게 재식별할 수 있어야 한다.

`ApplyGuardrail` action의 공통 verdict mapping은 유지한다.

| Bedrock action | EvaluationResult |
| --- | --- |
| `GUARDRAIL_INTERVENED` | `BLOCK` |
| `NONE` | `ALLOW` |

null 또는 알 수 없는 action은 정상 EvaluationResult가 아니다. Evaluation Profile 해석과 Guardrail version 고정은 #114, 오류 taxonomy와 자연어 응답 전달 방식은 #116에서 구현되었다. Worker 실행 경로 연결은 #117이 소유한다. Evaluation Profile CRUD, provider ensemble과 provider별 고급 설정 모델은 확정하지 않는다.

## 보안 경계

Application response는 Evaluator 입력으로만 사용하며 public API에 노출하지 않는다. Provider 원문 오류, assessment, output text, 사용자 input, Application response, ARN, 자격 증명과 stack trace는 승인된 저장·관측 계약 없이 DB·일반 로그에 노출하지 않는다. 외부 호출은 DB 트랜잭션 밖에서 수행하고, retry·timeout·stale 결과 차단은 Worker 계약과 함께 구현한다.

## 구현

`com.guardbench.evaluator.infrastructure.bedrock.BedrockGuardrailEvaluatorAdapter`는
`EvaluatorExecutionPort`를 구현한다. Adapter는 TestRun이 #114에서 고정한
`EvaluatorReference`로 `bedrock_guardrail_evaluator`를 조회하고, Application의 자연어 응답만
AWS SDK 요청으로 변환한다.

| 단계 | 소비자 소유 Port/저장 | Bedrock API |
| --- | --- | --- |
| Evaluator reference 등록 | `RegisterEvaluatorReferencePort` / `evaluator_reference` | 외부 호출 없음 |
| Application response 평가 | `EvaluatorExecutionPort` | `ApplyGuardrail` |

`ApplyGuardrail`은 `source=OUTPUT`, 단일 text content와 `INTERVENTIONS` output scope로 호출한다.
`NONE`은 `ALLOW`, `GUARDRAIL_INTERVENED`는 structured assessment를 검사해 `BLOCKED`가 있으면
`BLOCK`, `ANONYMIZED`만 있으면 `ALLOW`로 정규화한다. null·알 수 없는 action이나 assessment는
`PROVIDER_RESPONSE_INVALID`다. raw assessment, output text와 provider 오류 원문은 Port 밖으로
전달하지 않는다.

## MVP Evaluation Profile 경계

MVP가 지원하는 Evaluation Check는 `PII_LEAKAGE | HARMFUL_CONTENT`다. 이 Adapter는 AI Application
output만 `ApplyGuardrail(source=OUTPUT)`으로 평가하며, Bedrock `PROMPT_ATTACK`을 output evaluator로
사용하지 않는다. Prompt Injection 성공 여부 평가는 MVP 이후 별도 evaluator 후보로 남긴다.

#114의 evaluator catalog는 numbered revision을 접수 시 `EvaluatorReference`에 고정하므로
새 Evaluator 흐름은 DRAFT materialization을 지원하지 않는다. 기존 Target 전용
`CreateGuardrailVersion` 경계와 `bedrock_guardrail_target` 저장소는 Evaluator 실행 경로에서
사용하지 않는다. 따라서 Evaluator 호출은 `BedrockRuntimeClient`만 필요하며 AWS 호출은 DB
트랜잭션 밖에서 수행된다.

Bedrock SDK timeout/retry는 `guardbench.bedrock.*` 설정을 사용하며 전체 15초 한도를 execution
claim lease(45초)보다 짧게 유지한다. 오류는 `EVALUATOR_NOT_FOUND`,
`EVALUATOR_ACCESS_DENIED`, `EVALUATOR_CONFIGURATION_INVALID`, `PROVIDER_UNAVAILABLE`,
`PROVIDER_RESPONSE_INVALID`, `PROVIDER_TIMEOUT`으로 안전하게 수렴한다.

현재 `dev`에서 이 Adapter는 `EvaluatorExecutionPort` 구현으로 존재하며 #117 Worker가
Application response를 전달한다. Worker는 Evaluator의 `EvaluationResult`를 실행 결과로 저장하고,
ExpectedResult와 비교한 Assertion을 생성한다. Application response 자체는 내부 실행 결과로만
보존하며 public 결과에는 노출하지 않는다.

AWS 근거는 [ApplyGuardrail API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ApplyGuardrail.html)와
[독립 ApplyGuardrail 사용 가이드](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-use-independent-api.html)다.
