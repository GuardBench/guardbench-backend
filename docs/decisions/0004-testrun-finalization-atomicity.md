# 0004. TestRun 최종 평가와 종료의 원자성

> Status: DRAFT
> Owner: Backend
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: [GitHub Issue #28](https://github.com/GuardBench/guardbench-backend/issues/28)
> AI assistance: 이 문서는 LLM의 도움으로 작성되었으며 팀의 검토와 승인이 필요합니다.

- ADR Status: PROPOSED
- Decision date: 2026-08-24
- Related Issue: #28

## Context

승인된 API와 Evaluation 계약은 `qualityGate = null`을 평가 전 상태로, `NOT_EVALUATED`를 TestRun 처리는 끝났지만 평가 가능한 데이터가 없는 결과로 구분한다. TestRun의 `FINISHED`는 성공만을 뜻하지 않고 정상 완료, 부분 실행 실패와 대상 준비 오류를 포함해 처리가 종결된 터미널 상태다.

[ADR 0003](0003-result-aggregate-and-write-port-boundaries.md)은 `TestRun`과 `QualityGateResult`를 서로 다른 Aggregate Root로 두고 각 Repository Port를 분리한다. [ADR 0002](0002-postgresql-persistence-contract.md)의 `quality_gate_result.test_run_id` PK와 FK는 Run당 결과를 최대 한 건으로 제한하지만 다음 역방향 존재 조건까지 보장하지 못한다.

- 모든 FINISHED TestRun에는 QualityGateResult가 정확히 하나 존재한다.
- QualityGateResult가 존재하는 TestRun은 FINISHED다.

두 Aggregate를 별도 트랜잭션으로 저장하면 Polling 조회가 `FINISHED + qualityGate=null`을 관찰하거나 한쪽 저장 실패가 영구적인 부분 상태를 만들 수 있다. 반대로 Evaluation 결과를 TestRun Aggregate나 Repository에 포함하면 ADR 0003의 저장 경계와 `evaluation -> testrun` 의존 방향이 무너진다.

Worker 선점, 중복 메시지 처리 기록, 동시 실행 직렬화, 행 잠금 또는 CAS, 기술적 오류의 retry와 timeout은 비동기 실행 결정 #5에서 함께 정한다. 이 ADR은 Worker 방식과 무관하게 최종 평가의 Application 의미, 원자적 commit과 이미 완료된 결과의 재호출 의미를 결정한다.

## Decision

### 관찰 가능한 최종화 불변식

commit되어 외부에서 관찰 가능한 상태에 다음 양방향 불변식을 적용한다.

- `TestRun.status = FINISHED`이면 해당 `TestRunId`의 QualityGateResult가 정확히 하나 존재한다.
- QualityGateResult가 존재하면 해당 TestRun은 `FINISHED`다.
- 평가 가능한 결과가 있으면 Quality Gate는 `PASS` 또는 `FAIL`이다.
- 평가 가능한 데이터가 없으면 결과를 생략하지 않고 `NOT_EVALUATED`와 `metrics = null`을 저장한다.
- `FINISHED + qualityGate=null`과 `FINISHED 이전 + QualityGateResult 존재`를 유효한 저장 상태로 취급하지 않는다.

트랜잭션 내부에서 어느 Aggregate를 먼저 변경하는지는 구현 상세다. commit 전 중간 상태가 다른 트랜잭션에 노출되지 않고 최종 commit에 두 Aggregate가 함께 포함되어야 한다.

### Application 소유권과 의존 방향

`evaluation/application`의 최종화 유스케이스가 전체 과정을 조율한다.

1. `testrun`의 공개 Domain/Application 계약으로 TestRun과 평가에 필요한 실행 사실을 조회한다.
2. Evaluation 정책으로 QualityGateResult를 생성한다.
3. TestRun Aggregate에 `FINISHED` 전환을 요청한다.
4. `QualityGateResultRepository`와 `TestRunRepository`를 하나의 PostgreSQL 트랜잭션에서 호출한다.

Evaluation Application Service는 여러 Aggregate의 호출과 트랜잭션을 조율할 뿐 TestRun 상태 전이 규칙을 소유하지 않는다. TestRun Aggregate가 자신의 허용 상태, 실행 완료 조건, `executionOutcome`과 완료 시각 불변식을 검증한다.

이 배치는 ADR 0001과 ADR 0003의 의존 방향을 유지한다.

```text
Worker/Inbound Adapter
        |
        v
evaluation/application 최종화 유스케이스
        |-- evaluation/domain 정책과 QualityGateResultRepository
        `-- testrun/domain TestRun과 TestRunRepository

evaluation -> testrun
testrun -X-> evaluation
```

Repository는 각각 저장 대상 Aggregate의 소유 도메인에 둔다. Application Service가 두 Repository를 사용한다는 사실은 Aggregate 경계를 합치거나 Repository 소유권을 옮긴다는 뜻이 아니다.

### 최종화 가능 상태

- 정상 흐름은 `RUNNING` TestRun의 모든 고정 Snapshot 실행이 터미널일 때 최종화한다. 실행 결과에 따라 `executionOutcome`은 `COMPLETED` 또는 `INCOMPLETE`이고, 계산 가능한 데이터에 따라 Quality Gate는 `PASS`, `FAIL` 또는 `NOT_EVALUATED`다.
- Candidate materialization 등 대상 준비가 실패해 실행할 수 없는 경우에는 `PREPARING -> FINISHED` 예외 경로를 허용한다. 이때 `executionOutcome = ERROR`, Quality Gate는 `NOT_EVALUATED`다.
- `QUEUED`에서는 최종화하지 않는다.
- 이미 `FINISHED`인 TestRun은 새 상태 전이 대상이 아니며 아래 재호출 규칙을 적용한다.

Quality Gate 계산 예외, Repository 저장 실패 또는 commit 실패는 평가 데이터 부재가 아니다. 이를 `NOT_EVALUATED`로 변환하지 않고 트랜잭션 전체를 롤백하여 TestRun을 종료 전 상태로 유지한다. 기술적 실패가 retry 가능한지, retry 횟수와 terminal failure를 어떻게 처리할지는 #5에서 결정한다.

### 원자적 저장과 실패

QualityGateResult 저장과 TestRun의 `FINISHED` 전환은 하나의 PostgreSQL 트랜잭션이다.

- 두 저장이 모두 성공해야 commit한다.
- QualityGateResult 저장이 실패하면 TestRun 변경도 롤백한다.
- TestRun 저장이 실패하면 QualityGateResult 변경도 롤백한다.
- Evaluation 계산이 실패하면 저장과 상태 전이를 시작하지 않거나 이미 시작한 트랜잭션을 롤백한다.
- 읽기 Projection은 commit된 상태만 조합하며 `FINISHED + qualityGate=null`을 정상 상태로 보정하거나 숨기지 않는다.

### 이미 완료된 최종화의 재호출

최종화 입력은 TestRun을 식별한다. 같은 TestRun의 최종화가 다시 호출되었을 때 이미 `FINISHED`이고 QualityGateResult가 존재하면 기존 결과를 그대로 반환하는 멱등 성공으로 처리한다.

- 기존 QualityGateResult를 재계산하거나 덮어쓰지 않는다.
- 현재 Evaluation 정책이 달라졌더라도 이미 commit된 결과를 소급 변경하지 않는다.
- `FINISHED`인데 QualityGateResult가 없으면 멱등 성공이 아니라 저장 불변식 위반이다.
- QualityGateResult가 있는데 TestRun이 `FINISHED`가 아니어도 저장 불변식 위반이다.

중복 메시지의 선점과 동시 요청 중 승자를 정하는 방식은 #5 범위다. 해당 방식은 이 ADR의 기존 결과 반환과 덮어쓰기 금지 의미를 보존해야 한다.

### DB와 Application의 책임

| 보장 수단 | 책임 |
| --- | --- |
| `quality_gate_result.test_run_id` PK | Run당 QualityGateResult를 최대 한 건으로 제한 |
| TestRun FK | 존재하는 TestRun만 QualityGateResult가 참조하도록 보장 |
| Quality Gate CHECK | `PASS/FAIL`의 전체 metrics와 `NOT_EVALUATED`의 null metrics shape 보장 |
| Evaluation Application 트랜잭션 | FINISHED와 정확히 하나의 QualityGateResult를 함께 commit |
| TestRun Aggregate | 최종화 가능 상태와 lifecycle 불변식 검증 |
| PostgreSQL 통합 테스트 | 부분 저장, 롤백, 조회 불변식과 멱등 재호출 검증 |

일반 FK와 행 단위 CHECK만으로 `FINISHED -> QualityGateResult 존재`라는 역방향 cross-table 조건을 강제하기 어렵다. DB trigger나 순환 FK를 추가하지 않고 Application 트랜잭션과 통합 테스트를 보장 수단으로 사용한다.

## Alternatives

### 두 Aggregate를 별도 트랜잭션으로 저장

구현 호출은 단순하지만 어느 저장이 먼저든 부분 상태가 관찰되거나 영구히 남을 수 있어 기각한다.

### TestRun Aggregate나 Repository가 Quality Gate를 저장

한 저장 호출로 보일 수 있지만 `testrun -> evaluation` 의존과 타 도메인 결과 저장 책임이 생기므로 기각한다.

### DB trigger 또는 deferred constraint로 역방향 존재 강제

DB가 강하게 보장할 수 있지만 상태 전이와 Evaluation 의미가 trigger로 이동하고 순환 관계 및 운영 복잡도가 생긴다. 현재 PostgreSQL 단일 Application 트랜잭션으로 필요한 원자성을 보장할 수 있어 도입하지 않는다.

### 평가 불가 또는 기술적 실패에서 Quality Gate 행 생략

`qualityGate = null`의 평가 전 의미와 종료 후 평가 불가를 구분할 수 없다. 도메인 데이터 부족에는 `NOT_EVALUATED`를 저장하고 기술적 실패에는 롤백을 적용한다.

### 재호출에서 Quality Gate 재계산 또는 upsert

정책 변경이나 중복 처리 시 이미 공개된 결과가 바뀔 수 있고 결과 Aggregate의 불변 사실 의미를 훼손하므로 기각한다.

## Consequences

장점은 다음과 같다.

- Polling과 결과 조회가 FINISHED이면 항상 완성된 Quality Gate를 조합할 수 있다.
- 평가 데이터 부족과 기술적 최종화 실패를 혼동하지 않는다.
- Evaluation 정책 소유권과 TestRun lifecycle 소유권을 유지하면서 cross-Aggregate 원자성을 보장한다.
- 중복 재호출이 이미 공개된 결과를 바꾸지 않는다.
- DB trigger 없이 Repository Port와 Application 트랜잭션으로 구현할 수 있다.

비용과 위험은 다음과 같다.

- `evaluation/application`이 서로 다른 도메인의 Repository 두 개를 하나의 트랜잭션으로 조율해야 한다.
- 일반 FK만으로 양방향 불변식을 완전히 강제할 수 없어 Application 구현과 PostgreSQL 통합 테스트가 필수다.
- 저장 불변식이 이미 훼손된 데이터는 자동 복구하지 않고 운영 오류로 드러난다.
- Worker 동시 실행의 승자 결정과 기술적 실패 retry는 #5 결정 전까지 구현할 수 없다.

이 결정을 되돌리려면 새 ADR로 최종화 의미와 트랜잭션 경계를 supersede한다. 공개된 Quality Gate 결과를 일괄 재계산하거나 기존 결과를 덮어쓰는 방식으로 되돌리지 않는다.

## Validation

1. `RUNNING`의 모든 실행이 터미널이면 QualityGateResult와 FINISHED TestRun이 함께 commit되는지 검증한다.
2. Candidate 준비 실패에서 `PREPARING -> FINISHED / ERROR`와 `NOT_EVALUATED / metrics=null`이 함께 commit되는지 검증한다.
3. `QUEUED` 또는 실행이 끝나지 않은 `RUNNING`은 최종화가 거부되는지 검증한다.
4. QualityGateResult 저장 실패와 TestRun 저장 실패를 각각 주입해 두 Aggregate 변경이 모두 롤백되는지 검증한다.
5. Evaluation 계산 예외가 `NOT_EVALUATED`로 저장되지 않고 TestRun이 종료 전 상태를 유지하는지 검증한다.
6. 이미 FINISHED이며 QualityGateResult가 있는 TestRun의 재호출이 기존 결과를 반환하고 쓰기와 재계산을 수행하지 않는지 검증한다.
7. 한쪽만 존재하는 비정상 fixture를 읽으면 불변식 위반으로 처리하는지 검증한다.
8. Polling 조회가 commit된 FINISHED와 Quality Gate를 함께 관찰하고 정상 경로에서 `FINISHED + qualityGate=null`을 반환하지 않는지 검증한다.
9. Worker 선점, 동시 실행 직렬화, 행 잠금/CAS와 retry가 #5 범위로 추적되는지 확인한다.
10. Java, JPA Entity, Repository Adapter, Migration, 공개 API와 Quality Gate 판정 공식이 변경되지 않았는지 확인한다.
