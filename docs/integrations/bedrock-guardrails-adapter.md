# Bedrock Guardrail Evaluator Adapter 설계 근거

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-31
> Canonical source: GitHub
> Related: [ADR 0011](../decisions/0011-ai-application-target-and-guardrail-evaluator.md)

## 목표 역할

AWS Bedrock Guardrail은 AI Application Target이 아니라 첫 번째 Guardrail Evaluator 구현이다. AI Application이 반환한 자연어 ApplicationResponse를 평가하고 GuardBench 공통 `EvaluationResult(ALLOW | BLOCK)`로 정규화한다.

```text
AI Application Target
        ↓ Natural Language ApplicationResponse
Bedrock Guardrail Evaluator Adapter
        ↓ ApplyGuardrail
EvaluationResult(ALLOW | BLOCK)
```

Evaluator Adapter만 AWS SDK 타입, Guardrail identifier/version과 provider 응답을 알고, Core에는 소비자가 소유한 scalar/value 계약만 전달한다. TestRun은 실제 사용한 Evaluator 설정과 버전을 불변하게 재식별할 수 있어야 한다.

`ApplyGuardrail` action의 공통 verdict mapping은 유지한다.

| Bedrock action | EvaluationResult |
| --- | --- |
| `GUARDRAIL_INTERVENED` | `BLOCK` |
| `NONE` | `ALLOW` |

null 또는 알 수 없는 action은 정상 EvaluationResult가 아니다. 오류 taxonomy, 자연어 응답 전달 방식, EvaluatorReference와 Guardrail version 고정의 구체 구현은 #114·#116·#117이 소유한다. 이 문서는 Evaluation Profile이나 provider별 공통 설정 모델을 확정하지 않는다.

## 보안 경계

Provider 원문 오류, assessment, output text, 사용자 input, Application response, ARN, 자격 증명과 stack trace는 승인된 저장·관측 계약 없이 DB·API·일반 로그에 노출하지 않는다. 외부 호출은 DB 트랜잭션 밖에서 수행하고, retry·timeout·stale 결과 차단은 Worker 계약과 함께 구현한다.

## 현재 구현

현재 `com.guardbench.target.infrastructure.bedrock` Adapter는 `BEDROCK_GUARDRAIL` Target의 준비와 실행을 담당한다.

| 현재 단계 | 현재 소비자 소유 Port | Bedrock API |
| --- | --- | --- |
| Target 등록 | `RegisterTargetReferencePort` | 외부 호출 없음 |
| DRAFT 준비 | `TargetPreparationPort` | `CreateGuardrailVersion` |
| Snapshot input 실행 | `TargetExecutionPort` | `ApplyGuardrail` |

현재 등록·준비 계약은 다음과 같다.

- `BEDROCK_GUARDRAIL` identifier와 `DRAFT` 또는 1~8자리 numbered revision을 받는다.
- numbered revision은 등록 시 resolved revision으로 고정한다.
- DRAFT는 `PREPARING`에서 `CreateGuardrailVersion`으로 materialize한다.
- `guardbench-test-run-{testRunId}` 형태의 결정적 `clientRequestToken`을 사용하고, 이미 resolved revision이 있으면 준비를 반복하지 않는다.
- AWS 호출은 DB 트랜잭션 밖에서 수행한다.

AWS 근거는 [CreateGuardrailVersion API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_CreateGuardrailVersion.html), [ApplyGuardrail API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ApplyGuardrail.html), [독립 ApplyGuardrail 사용 가이드](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-use-independent-api.html)다.

현재 코드는 Guardrail identifier와 requested/resolved revision을 Target 저장소에 두고, Snapshot input을 `source=INPUT`으로 직접 평가해 Target `ActualResult`를 만든다. 현재 null/알 수 없는 action과 SDK 예외는 `TARGET_NOT_FOUND`, `TARGET_ACCESS_DENIED`, `TARGET_CONFIGURATION_INVALID`, `PROVIDER_TIMEOUT`, `PROVIDER_UNAVAILABLE`, `PROVIDER_RESPONSE_INVALID`의 안전한 오류로 정규화한다. SDK timeout/retry는 `guardbench.bedrock.*` 설정을 사용하고 Application claim은 stale 응답의 저장을 차단한다. 이 구조는 current implementation 기록이며 목표 Evaluator 계약이 아니다. #114에서 Guardrail Target 의존을 제거하고 #116에서 Evaluator Adapter로 전환한다.

## 범위

#113은 문서 계약만 정리한다. Java, Migration, AWS 호출 코드, OpenAPI schema와 물리 ERD는 변경하지 않는다.
