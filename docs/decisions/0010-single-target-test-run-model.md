# 0010. TestRun 단일 Target 실행 모델

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Origin: GitHub Issue #106
> Related: [ADR 0013](0013-response-behavior-classifier.md)

- ADR Status: ACCEPTED
- Decision date: 2026-08-30
- Related Issue: #106

## Context

TestRun은 테스트 실행 자체와 Regression 비교를 분리해야 한다. 하나의 Run이 복수 Target 역할을 동시에 소유하면 실행 수명주기와 비교 수명주기가 결합되고, Snapshot별 실행 수와 비동기 작업 식별도 불필요하게 복잡해진다.

현재 GuardBench는 하나의 AI Application을 한 번 실행한 결과를 저장하고, Regression이 필요할 때 동일한 테스트 정의와 classifier 조건을 만족하는 완료 Run의 저장 결과를 조회해 비교한다.

## Decision

1. TestRun은 소스 TestSuite와 하나의 `TargetReference`만 보유한다.
2. 현재 Target은 OpenAI-compatible `HTTP_ENDPOINT` AI Application이다.
3. Target의 endpoint URL, model, optional revision은 Target Context가 소유하고 TestRun Domain은 불투명 `TargetReference`만 보유한다.
4. Snapshot당 `TestExecution`은 하나이며 `test_execution`과 `test_execution_claim`의 식별자는 `snapshot_id`다.
5. TestRun은 실행 당시 사용한 Response Behavior Classifier를 `EvaluatorReference`로 고정한다.
6. `TestExecutionRequested`와 `TestExecutionCompleted` v2 payload는 Target 역할 구분값을 포함하지 않으며 deduplication key는 `{eventType}:{snapshotId}`다.
7. TestRun 실행 중 Regression 결과를 생성하지 않는다. Regression은 완료된 Run들의 Snapshot 정의와 저장된 evaluator verdict를 조회해 계산한다.
8. resolution/execution claim, provider retry, stale claim 차단, terminal 결과와 Outbox의 원자 저장, TestRun 최종화 트랜잭션 보장은 유지한다.

## Consequences

- Snapshot 수와 execution 수가 1:1이 된다.
- HTTP Application 실행과 Regression 비교의 책임이 분리된다.
- Target 실행 결과는 Application response와 classifier verdict로 저장된다.
- 동일한 Snapshot 정의와 classifier 조건을 만족하는 과거 완료 Run을 Regression 비교 대상으로 사용할 수 있다.
- Target 및 classifier 세부 실행 계약은 ADR 0013을 따른다.

## Validation

- 신규 TestRun 생성은 `HTTP_ENDPOINT`만 허용한다.
- Snapshot마다 `TestExecution`이 최대 하나 존재한다.
- TestRun은 `TargetReference`와 `EvaluatorReference`를 모두 고정한다.
- Regression 비교는 Application Target 또는 classifier를 재호출하지 않고 저장된 완료 Run 결과를 사용한다.
- 비동기 execution/claim/outbox 테스트가 단일 Target 식별 규칙을 검증한다.
