# Bedrock Guardrails Target Adapter 설계 근거

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-08-30
> Canonical source: GitHub
> Related: [ADR 0010](../decisions/0010-single-target-test-run-model.md)

Bedrock Guardrails는 GuardBench의 상위 Domain이 아니라 Target 경계의 하나의 provider 구현이다. AWS SDK를 아는 코드는 `com.guardbench.target.infrastructure.bedrock`에만 두고, TestRun Application이 소유한 provider-independent Port와 값 계약으로 변환한다.

## 경계

| 단계 | 소비자 소유 Port | Bedrock API |
| --- | --- | --- |
| Target 등록 | `RegisterTargetReferencePort` | 외부 호출 없음 |
| DRAFT 준비 | `TargetPreparationPort` | `CreateGuardrailVersion` |
| Snapshot input 실행 | `TargetExecutionPort` | `ApplyGuardrail` |

TestRun은 `TargetReference`만 전달한다. Adapter는 Target 저장소에서 `guardrailIdentifier`, `requestedRevision`, `resolvedRevision`을 조회하며 이 값을 TestRun Domain에 복제하지 않는다.

## 등록과 준비

- 공개 Target type은 `BEDROCK_GUARDRAIL`이다.
- revision은 `DRAFT` 또는 `[1-9][0-9]{0,7}` numbered revision이다.
- numbered revision은 등록 시 `resolvedRevision`으로 고정한다.
- DRAFT는 PREPARING에서 `CreateGuardrailVersion`으로 materialize한다. `guardbench-test-run-{testRunId}` 형태의 결정적 `clientRequestToken`을 사용한다.
- 이미 `resolvedRevision`이 있으면 준비 호출을 반복하지 않는다.
- AWS 호출은 DB 트랜잭션 밖에서 수행하고, 성공 후 확정 revision을 Target 저장소에 반영한다.

AWS 근거: [CreateGuardrailVersion API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_CreateGuardrailVersion.html), [ApplyGuardrail API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ApplyGuardrail.html), [독립 ApplyGuardrail 사용 가이드](https://docs.aws.amazon.com/bedrock/latest/userguide/guardrails-use-independent-api.html).

## 실행과 정규화

`ApplyGuardrail` 요청은 Target 저장소의 identifier/resolved revision, Snapshot input, `source=INPUT`, `outputScope=INTERVENTIONS`를 사용한다. DRAFT 상태로는 실행하지 않는다.

| Bedrock action | Target action |
| --- | --- |
| `GUARDRAIL_INTERVENED` | `BLOCK` |
| `NONE` | `ALLOW` |

null/알 수 없는 action은 `PROVIDER_RESPONSE_INVALID`다. SDK 예외은 `TARGET_NOT_FOUND`, `TARGET_ACCESS_DENIED`, `TARGET_CONFIGURATION_INVALID`, `PROVIDER_TIMEOUT`, `PROVIDER_UNAVAILABLE`, `PROVIDER_RESPONSE_INVALID`의 안전한 코드로 매핑한다. Provider 원문 오류, assessment, output text, input content, ARN, stack trace는 Port·Domain·DB·API·일반 로그에 전달하지 않는다.

## Retry와 timeout

- SDK 전체 timeout, 개별 시도 timeout, SDK retry는 `guardbench.bedrock.*` 설정을 사용한다.
- Application retry, claim lease, stale token 차단은 TestRun Worker 계약을 유지한다.
- `ApplyGuardrail`은 fencing token을 받지 않으므로 lease 경계에서 provider 호출이 중복될 수 있지만 stale 응답은 terminal TestExecution에 반영하지 않는다.

## 범위 외

다른 provider, 범용 HTTP auth, 자연어 출력, provider raw payload 저장, 새 Quality Gate, comparison/regression 생성은 #106 범위가 아니다.
