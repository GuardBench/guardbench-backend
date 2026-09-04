# 0013. Response Behavior Classifier 실행 계약

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Related Issue: [#173](https://github.com/GuardBench/guardbench-backend/issues/173)
> Operational follow-up: [#182](https://github.com/GuardBench/guardbench-backend/issues/182)

- ADR Status: ACCEPTED
- Decision date: 2026-09-04
- Supersedes: 기존 profile/catalog 및 이전 provider 전용 evaluator 실행 설명

## Context

GuardBench는 AI Application의 자연어 응답을 TestCase의 기대 동작과 비교해야 한다. provider-specific 정책과 사용자가 제출하는 평가 profile을 실행 계약에 포함하면, TestRun API와 저장 모델이 특정 provider 설정에 결합되고 응답 행동 자체를 측정하기 어렵다.

## Decision

1. TestRun은 하나의 OpenAI-compatible `HTTP_ENDPOINT` AI Application Target을 실행한다.
2. TestRun 생성 요청은 evaluator provider, endpoint, model, system prompt 또는 평가 profile을 받지 않는다.
3. Worker는 TestCase prompt와 ApplicationResponse를 Response Behavior Classifier에 함께 전달한다.
4. 현재 classifier Adapter는 SageMaker Runtime `InvokeEndpoint`를 사용한다. endpoint와 prompt configuration은 서버 배포 configuration이 소유한다.
5. SageMaker endpoint 요청은 OpenAI-compatible chat completions JSON이며 `temperature: 0`, `max_tokens: 8`, `chat_template_kwargs.enable_thinking: false`를 포함한다.
6. `choices[0].message.content`가 정확히 `COMPLY`이면 `EvaluationResult.ALLOW`, 정확히 `REFUSE`이면 `EvaluationResult.BLOCK`으로 정규화한다.
7. 빈 응답, 예기치 않은 classifier output, timeout 또는 provider failure는 `ALLOW`·`BLOCK`으로 fallback하지 않고 TestExecution failure로 저장한다.
8. ExpectedResult, Assertion, Quality Gate, Regression의 의미는 유지한다. 비교 가능성은 동일한 Snapshot 정의와 classifier contract version을 전제로 한다.

```text
TestCaseSnapshot.prompt + ApplicationResponse
                    ↓
SageMaker Runtime Classifier
                    ↓
          COMPLY | REFUSE
                    ↓
          ALLOW   | BLOCK
                    ↓
             Assertion
```

## Consequences

- TestRun API에서 평가 profile과 provider-specific 입력이 제거된다.
- classifier는 행동 분류만 담당하고 도메인 정책, 안전성, 정확성, Regression과 Quality Gate를 판정하지 않는다.
- 실제 사용한 classifier contract version은 Run과 비교 가능성 판단에 사용한다.
- SageMaker Real-Time endpoint는 비활성화할 때 model, endpoint configuration, execution role과 PrivateLink를 유지하고 billable endpoint만 삭제할 수 있다.
- classifier output과 ApplicationResponse는 public API에 노출하지 않는다.

## Validation

1. OpenAPI TestRun 생성 요청에 별도 평가 profile 입력이 없다.
2. `COMPLY → ALLOW`, `REFUSE → BLOCK` normalization을 검증한다.
3. prompt와 ApplicationResponse가 모두 classifier에 전달되는지 검증한다.
4. 잘못된 output과 provider failure가 임의 action으로 변환되지 않는지 검증한다.
5. SageMaker endpoint disabled/enabled 운영 절차와 최소 IAM·PrivateLink 계약은 [SageMaker Adapter 문서](../integrations/sagemaker-classifier-adapter.md)에 기록한다.
