# 0003. 실행·평가 결과 Aggregate와 write-side Port 경계

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-04
> Canonical source: GitHub
> Origin: [GitHub Issue #27](https://github.com/GuardBench/guardbench-backend/issues/27)
> Related: [ADR 0010](0010-single-target-test-run-model.md), [ADR 0013](0013-response-behavior-classifier.md)

- ADR Status: ACCEPTED
- Decision date: 2026-08-24
- Related Issue: #27

## Context

GuardBench는 Snapshot 실행 결과, Snapshot 단위 평가 결과, TestRun 최종 평가를 서로 다른 수명주기로 저장한다. 물리 테이블 하나가 곧 Aggregate 하나를 의미하지 않으며, 저장 경계는 함께 지켜야 하는 불변식과 동시성 기준으로 정한다.

현재 TestRun은 Snapshot마다 하나의 AI Application 실행을 수행하고 Response Behavior Classifier verdict를 저장한다.

## Decision

### Aggregate와 식별자

| 소유 도메인 | Aggregate Root | 식별자 | 핵심 의미 |
| --- | --- | --- | --- |
| `testrun` | `TestExecution` | `TestExecutionId(TestCaseSnapshotId)` | Snapshot 하나의 terminal 실행 결과 |
| `evaluation` | `SnapshotEvaluation` | `TestCaseSnapshotId` | expected action과 evaluator verdict의 Snapshot 평가 |
| `evaluation` | `QualityGateResult` | `TestRunId` | TestRun 전체의 최종 평가 |

별도 scalar execution ID를 만들지 않는다. `test_execution`의 물리 PK는 `snapshot_id`다.

### TestExecution

`TestExecution`은 Snapshot의 AI Application 실행과 classifier 평가 결과를 표현한다.

- 상태는 `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `NOT_STARTED` 중 하나다.
- `SUCCEEDED`에는 Application response와 `ALLOW | BLOCK` evaluator verdict가 존재한다.
- 실패/timeout에는 안전하게 가공한 `errorStage`, `errorCode`, `errorMessage`를 저장한다.
- 같은 Snapshot의 terminal 결과를 다른 의미로 암묵적으로 덮어쓰지 않는다.
- 실행 중 선점은 Aggregate 상태가 아니라 별도 `test_execution_claim` infrastructure table이 관리한다.

### SnapshotEvaluation

`SnapshotEvaluation`은 Snapshot 단위 평가 결과의 Aggregate Root다.

- evaluator verdict가 존재하면 ExpectedResult와 비교한 Assertion을 계산한다.
- `AssertionResult`는 SnapshotEvaluation 내부 결과이며 별도 Aggregate Root로 취급하지 않는다.
- 현재 persistence에는 `change_result` shape가 존재하지만 Regression API는 완료된 두 Run의 Snapshot 정의와 저장 verdict를 조회해 비교한다.
- Regression 비교를 위해 AI Application이나 classifier를 다시 실행하지 않는다.

### QualityGateResult

`QualityGateResult`는 TestRun 단위 최종 평가다.

- 식별자는 `TestRunId`다.
- `PASS`, `FAIL`, `NOT_EVALUATED` 상태를 가진다.
- `PASS | FAIL`에는 `assertionPassRate`, `executionSuccessRate`가 존재한다.
- `NOT_EVALUATED`에는 metrics가 없다.
- Quality Gate 저장과 TestRun `FINISHED` 전환의 원자성은 ADR 0004가 소유한다.

### Repository Port

| Port | 선언 패키지 | 책임 |
| --- | --- | --- |
| `TestExecutionRepository` | `testrun/domain/repository` | Snapshot ID 기반 execution 조회/저장 |
| `SnapshotEvaluationRepository` | `evaluation/domain/repository` | Snapshot 평가 Root 조회/저장 |
| `QualityGateResultRepository` | `evaluation/domain/repository` | TestRun 최종 평가 조회/저장 |

Repository는 Aggregate 전체를 저장한다. `AssertionResultRepository` 같은 내부 결과별 Repository를 추가하지 않는다.

### 의존 방향

Evaluation과 TestRun Context 간 Java 타입 공유는 ADR 0006의 경계를 따른다. 다른 Context의 Repository를 직접 호출하지 않고 Application/Integration Port를 통해 필요한 사실만 전달한다.

## Consequences

- Snapshot 하나에 execution 하나라는 현재 실행 모델과 DB PK가 일치한다.
- Application response와 classifier verdict가 execution 결과에서 분리되지 않는다.
- 현재 Run의 Assertion/Quality Gate와 과거 Run과의 Regression 비교가 서로 다른 책임으로 유지된다.
- 물리 persistence 변경은 ADR 0002와 함께 검토한다.

## Validation

- `TestExecutionId`는 `TestCaseSnapshotId` 하나만 소유한다.
- 동일 Snapshot에 두 개의 TestExecution을 저장할 수 없다.
- 성공/실패 execution shape를 Domain 및 PostgreSQL 통합 테스트로 검증한다.
- SnapshotEvaluation/QualityGateResult Repository round-trip을 검증한다.
