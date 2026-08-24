# 0003. 실행·평가 결과 Aggregate와 write-side Port 경계

> Status: DRAFT
> Owner: Backend
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: [GitHub Issue #27](https://github.com/GuardBench/guardbench-backend/issues/27)
> AI assistance: 이 문서는 LLM의 도움으로 작성되었으며 팀의 검토와 승인이 필요합니다.

- ADR Status: PROPOSED
- Decision date: 2026-08-24
- Related Issue: #27

## Context

[ADR 0001](0001-domain-type-ownership-and-aggregate-boundaries.md)은 `TestExecution`, `AssertionResult`, `ChangeResult`, `QualityGateResult`의 타입 소유권과 단방향 의존을 정했지만 세부 Aggregate와 저장 단위는 실제 Persistence 구현 전에 별도로 확정하도록 남겼다. [ADR 0002](0002-postgresql-persistence-contract.md)는 네 결과를 위한 물리 테이블을 제안하지만, 어떤 Aggregate를 어느 write-side Port로 저장할지는 정하지 않았다.

이 미결정을 구현 단계로 넘기면 다음 문제가 생긴다.

- 비동기로 독립 완료되는 Baseline/Candidate 실행 결과를 저장하기 위해 불변 Snapshot이나 TestRun 전체를 적재·저장할 수 있다.
- `AssertionResult`와 `ChangeResult`를 테이블별 Aggregate로 오해해 동일 Snapshot 평가의 일관성 경계가 분리될 수 있다.
- TestRun 또는 Snapshot Repository에 Evaluation 결과 저장 책임이 섞일 수 있다.
- 읽기 Projection Port를 결과 저장에 재사용하거나 구현 단계에서 승인되지 않은 write-side Port를 추가할 수 있다.
- Evaluation 정책과 결과 구조의 변경이 `testrun`으로 역전파될 수 있다.

물리 테이블 하나가 곧 Aggregate 하나를 의미하지 않는다. Aggregate는 함께 지켜야 하는 불변식, 생성·변경 수명주기와 동시성 경계를 기준으로 정하고 Persistence Adapter가 그 경계를 하나 이상의 테이블에 매핑한다.

## Decision

실행 결과, Snapshot 평가 결과와 Run 최종 평가 결과를 서로 다른 수명주기 경계로 분리한다.

### Aggregate와 식별자

| 소유 도메인 | Aggregate Root | 식별자 | 내부 객체와 핵심 불변식 |
| --- | --- | --- | --- |
| `testrun` | `TestExecution` | `TestExecutionId(TestCaseSnapshotId, TargetType)` | 하나의 Snapshot과 `BASELINE` 또는 `CANDIDATE` target에 대한 터미널 실행 결과다. `SUCCEEDED`에만 `ActualResult`가 있고 실패·timeout·미시작에는 없다. |
| `evaluation` | `SnapshotEvaluation` | 기존 `TestCaseSnapshotId` | Candidate ActualResult가 있을 때 생성되며 `AssertionResult`를 반드시 소유한다. Baseline ActualResult도 있으면 비교 가능성과 변화 의미를 표현하는 `ChangeResult`를 선택적으로 소유한다. |
| `evaluation` | `QualityGateResult` | 기존 `TestRunId` | TestRun 전체의 최종 평가 결과다. `PASS` 또는 `FAIL`에는 전체 metrics가 있고 `NOT_EVALUATED`에는 metrics가 없다. |

`TestExecutionId`는 실행 Aggregate의 복합 식별자를 잘못 조합하지 않도록 `testrun/domain`이 소유하는 Value Object다. 별도 scalar 실행 ID나 DB sequence를 추가하지 않으며 물리 PK `(snapshot_id, target_type)`에 매핑한다.

`SnapshotEvaluationId`와 `QualityGateResultId`는 새로 만들지 않는다. 다른 Aggregate의 식별자를 같은 의미의 새 래퍼로 중복하지 않고 각각 `TestCaseSnapshotId`와 `TestRunId`를 그대로 사용한다.

### TestExecution Aggregate

Baseline과 Candidate는 같은 Snapshot을 사용하지만 서로 독립적으로 호출되고 터미널 상태에 도달한다. 한쪽 실행 결과를 저장하기 위해 `TestCaseSnapshot`이나 `TestRun` 전체를 수정하지 않는다. 따라서 `TestExecution`을 별도 Aggregate Root로 둔다.

- `TestExecutionId`가 Snapshot과 target을 함께 고정한다.
- 저장되는 상태는 `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `NOT_STARTED` 중 하나다.
- `ActualResult`, 안전하게 가공한 오류와 실행 시각의 조합은 승인된 Evaluation 계약을 따른다.
- 같은 ID의 실행 결과를 다른 의미의 결과로 암묵적으로 덮어쓰지 않는다.
- 중복 메시지, retry와 충돌 처리의 구체적인 멱등 규칙은 비동기 실행 결정 #5에서 확정한다.

### SnapshotEvaluation Aggregate

`SnapshotEvaluation`은 Snapshot 단위로 실제 생성된 평가 결과의 Aggregate Root다. Candidate ActualResult가 없으면 Assertion도 만들 수 없으므로 Aggregate 자체를 생성하지 않는다.

- Candidate ActualResult가 있으면 ExpectedResult와 비교한 `AssertionResult`를 반드시 생성한다.
- Baseline과 Candidate ActualResult가 모두 있으면 `ChangeResult`도 생성한다. 비교 조건에 따라 `COMPARABLE`과 `changeType`, 또는 `NOT_COMPARABLE`과 빈 `changeType`을 표현한다.
- Baseline ActualResult가 없으면 `ChangeResult`는 없다.
- `AssertionResult`와 `ChangeResult`는 독립 Aggregate Root가 아닌 `SnapshotEvaluation` 내부의 불변 결과 객체다.
- 두 내부 결과는 별도 식별자와 Repository를 갖지 않고 Root의 `TestCaseSnapshotId`를 공유한다.
- Root는 내부 결과가 서로 다른 Snapshot의 입력에서 만들어진 상태를 허용하지 않는다.

물리적으로 `assertion_result`의 `snapshot_id` 행이 Aggregate의 필수 부분이고 `change_result` 행은 선택적 부분이다. Persistence Adapter는 하나의 `SnapshotEvaluation`을 두 테이블에 매핑하며, Root 저장 중 생성되는 모든 행을 하나의 트랜잭션으로 처리한다. 별도 `snapshot_evaluation` 테이블은 도입하지 않는다.

### QualityGateResult Aggregate

`QualityGateResult`는 여러 Snapshot 평가와 실행 성공률을 집계한 TestRun 단위의 최종 평가 결과이므로 `SnapshotEvaluation`과 다른 Aggregate Root로 둔다.

- 식별자는 평가 대상인 `TestRunId`다.
- `PASS`, `FAIL`, `NOT_EVALUATED`와 metrics shape 불변식을 스스로 보장한다.
- Snapshot별 결과와 독립된 Repository를 사용하며 TestRun/Snapshot Repository에 저장 책임을 추가하지 않는다.
- Quality Gate 저장과 TestRun의 `FINISHED` 전환을 원자화하는 Application 트랜잭션, 동시성 및 재진입 규칙은 후속 결정 #28에서 확정한다.

### Repository Port

각 Aggregate Root에는 소유 도메인의 Repository Port를 하나씩 둔다.

| Port | 선언 패키지 | 최소 책임 | 구현 위치 |
| --- | --- | --- | --- |
| `TestExecutionRepository` | `testrun/domain/repository` | `TestExecutionId`로 Root를 조회하고 완전한 `TestExecution`을 저장 | `testrun/infrastructure/persistence` |
| `SnapshotEvaluationRepository` | `evaluation/domain/repository` | `TestCaseSnapshotId`로 Root를 조회하고 필수 Assertion과 선택적 Change를 하나의 Root로 저장 | `evaluation/infrastructure/persistence` |
| `QualityGateResultRepository` | `evaluation/domain/repository` | `TestRunId`로 Root를 조회하고 완전한 `QualityGateResult`를 저장 | `evaluation/infrastructure/persistence` |

구체 Java 이름은 다음 최소 형태를 구현 기준으로 사용한다.

```java
public interface TestExecutionRepository {
    Optional<TestExecution> findById(TestExecutionId id);
    void save(TestExecution execution);
}

public interface SnapshotEvaluationRepository {
    Optional<SnapshotEvaluation> findById(TestCaseSnapshotId snapshotId);
    void save(SnapshotEvaluation evaluation);
}

public interface QualityGateResultRepository {
    Optional<QualityGateResult> findById(TestRunId testRunId);
    void save(QualityGateResult result);
}
```

각 `save`는 Root 전체를 저장한다. 내부 결과별 `AssertionResultRepository`, `ChangeResultRepository`를 만들지 않는다. 서로 다른 Aggregate를 하나의 저장 단위처럼 감추는 범용 `EvaluationResultStore`도 도입하지 않는다. 여러 Aggregate의 Repository 호출과 트랜잭션 조율은 Application Service의 책임이다.

결과 Aggregate는 이미 확정된 실행·평가 사실을 나타내므로 같은 식별자의 다른 결과를 암묵적 upsert로 덮어쓰지 않는다. 동일 결과 재전달의 멱등 성공, 충돌과 retry의 구체 의미는 #5와 #28에서 확정하되 Repository Adapter는 그 결정을 구현할 수 있도록 DB PK 충돌을 숨겨 의미가 다른 결과를 갱신하지 않는다.

### Application과 의존 방향

ADR 0001의 다음 의존 방향을 유지한다.

```text
evaluation -> testrun
testrun -X-> evaluation
```

- `testrun`은 Snapshot, 실행 상태와 `ActualResult` 같은 실행 사실을 소유한다.
- `evaluation/application`은 `testrun`의 공개 Domain/Application 계약에서 안정적인 실행 사실을 입력받아 Snapshot Evaluation과 Quality Gate를 생성한다.
- `testrun`은 Evaluation의 Metric, 임계값, Assertion·Change·Quality Gate 타입이나 저장 구조에 의존하지 않는다.
- Evaluation 계산식과 정책이 바뀌어도 필요한 실행 사실의 계약이 유지되는 한 `testrun` 코드는 변경하지 않는다.
- 공개 TestRun 조회를 위한 기존 `testrun/application/query` Projection Port와 `evaluation/infrastructure/query` 구현은 변경하지 않고 write-side Repository로 재사용하지 않는다.

Application Service가 여러 Aggregate를 한 트랜잭션에서 조정할 수 있다는 사실은 Aggregate 경계를 합친다는 뜻이 아니다. 한 Aggregate가 다른 Aggregate의 Repository를 직접 호출하지 않는다.

### 규범적 패키지 위치

```text
src/main/java/com/guardbench/
├── testrun/
│   ├── domain/
│   │   ├── TestExecution.java                    [AR]
│   │   ├── TestExecutionId.java                  [VO: Snapshot + target]
│   │   └── repository/
│   │       └── TestExecutionRepository.java      [Port]
│   └── infrastructure/persistence/               [Port 구현]
└── evaluation/
    ├── domain/
    │   ├── SnapshotEvaluation.java               [AR]
    │   ├── AssertionResult.java                  [내부 불변 결과]
    │   ├── ChangeResult.java                     [내부 불변 결과]
    │   ├── QualityGateResult.java                [AR]
    │   └── repository/
    │       ├── SnapshotEvaluationRepository.java [Port]
    │       └── QualityGateResultRepository.java  [Port]
    ├── application/                              [평가 생성과 Repository 조율]
    └── infrastructure/persistence/               [Port 구현]
```

이 문서는 패키지와 계약을 정하며 Java 파일, JPA Entity와 Adapter를 선제 생성하지 않는다.

## Alternatives

### 기존 네 Repository만 유지

TestExecution과 Evaluation 결과를 기존 `TestRunRepository` 또는 `TestCaseSnapshotRepository`에 저장할 수 있다. Port 수는 적지만 불변 Snapshot과 독립 결과의 수명주기가 섞이고 `testrun` Repository가 Evaluation 결과 저장을 알게 되므로 기각한다.

### 물리 테이블마다 Aggregate와 Repository를 생성

`AssertionResultRepository`와 `ChangeResultRepository`를 각각 두면 테이블과 Port가 일대일로 대응한다. 그러나 두 결과는 동일 Snapshot과 Candidate ActualResult를 공유하는 하나의 평가에서 파생된다. 테이블 구조가 Domain 일관성 경계를 결정하게 되고 서로 다른 Snapshot 평가가 섞이는 상태를 Application이 반복 검증해야 하므로 기각한다.

### 수명주기별 세 Aggregate

`TestExecution`, `SnapshotEvaluation`, `QualityGateResult`를 독립 Root로 두면 비동기 target 실행, Snapshot 평가와 Run 집계 평가의 변경 이유와 저장 단위가 일치한다. 별도 Domain 타입과 세 Repository가 필요하지만 후속 구현이 다른 Aggregate 전체를 적재하지 않고 명확한 write-side Port를 사용할 수 있으므로 선택한다.

### 범용 EvaluationResultStore

Assertion, Change와 Quality Gate를 하나의 Store가 저장하면 Application 호출 수는 줄어든다. 하지만 Snapshot 단위 결과와 Run 단위 결과의 Aggregate 경계를 감추고 Store가 유스케이스 트랜잭션까지 소유하기 쉬우므로 도입하지 않는다.

## Consequences

장점은 다음과 같다.

- 비동기 Baseline/Candidate 결과를 Snapshot과 TestRun 전체를 다시 저장하지 않고 독립적으로 기록한다.
- Assertion과 Change가 같은 Snapshot 평가에서 생성된다는 사실을 Domain 객체와 Repository 경계로 표현한다.
- Evaluation 결과 저장 책임이 TestRun/Snapshot Repository로 침투하지 않는다.
- Evaluation 정책과 결과 구조가 바뀌어도 `testrun`은 안정적인 실행 사실과 lifecycle 계약만 유지한다.
- 물리 테이블 수와 Domain Aggregate 수가 다를 수 있음을 명시해 JPA 구조가 Domain 경계를 역으로 결정하지 않게 한다.

비용과 위험은 다음과 같다.

- ADR 0001이 남긴 미결정을 해소하면서 Root 세 개와 Repository Port 세 개가 구현 계약에 추가된다.
- `SnapshotEvaluationRepository` Adapter는 한 Root를 두 테이블에 매핑하고 선택적 Change 행을 복원해야 한다.
- 결과 재전달, 동시 최종화와 여러 Aggregate의 트랜잭션 조율은 #5와 #28의 후속 결정이 필요하다.
- Aggregate 경계가 실제 구현 경험과 맞지 않으면 승인 전 이 제안을 변경할 수 있고, 승인 후에는 새 ADR로 supersede해야 한다.

## Validation

1. `TestExecution`, `AssertionResult`, `ChangeResult`, `QualityGateResult`의 소유 Aggregate와 식별자를 위 표에서 모두 추적한다.
2. 각 Root의 쓰기가 세 Repository Port 중 하나로 추적되고 내부 결과별 Repository나 범용 Store가 없는지 확인한다.
3. Candidate ActualResult가 없으면 `SnapshotEvaluation`이 없고, Candidate만 성공하면 Assertion-only, 두 target에 ActualResult가 있으면 비교 가능 여부와 무관하게 Assertion과 Change가 같은 Root에 존재하는 시나리오를 검토한다.
4. Baseline과 Candidate가 독립적으로 완료되어도 `TestExecutionId`와 PK가 target별 결과를 하나로 제한하는지 확인한다.
5. `testrun`이 Evaluation Domain 타입에 의존하지 않고 `evaluation -> testrun` 방향만 유지하는지 후속 ArchUnit 테스트로 검증한다.
6. ADR 0002의 테이블별 Aggregate 매핑과 Repository 목록이 이 결정과 일치하는지 확인한다.
7. #8, #9, #14가 새 Aggregate 또는 write-side Port를 임의로 결정하지 않고 구현 가능한지 검토한다.
8. Java, JPA Entity, Repository Adapter, Migration, 공개 API와 기존 Query Projection Port가 변경되지 않았는지 확인한다.
