# 비동기 TestRun 계약 라우팅

> Status: DRAFT
> Owner: Backend
> Last reviewed: 2026-08-25
> Canonical source: GitHub
> Origin: [GitHub Issue #49](https://github.com/GuardBench/guardbench-backend/issues/49)

이 문서는 비동기 TestRun 구현에서 질문별로 필요한 **APPROVED** 계약을 찾기 위한 라우팅 인덱스다. ADR·ERD·Migration을 복사하거나 새 동작·DB 구조를 정하지 않는다. `DRAFT` 문서 자체를 구현 근거로 사용하지 않으며, 각 행의 APPROVED source가 항상 우선한다.

## 질문별 계약 라우팅

| 구현·검색 질문 | 먼저 읽을 APPROVED source | 후속 작업 |
| --- | --- | --- |
| `test_run`, `test_case_snapshot`, `test_execution`, 평가 결과의 PostgreSQL 컬럼·PK/FK·CHECK·index는? | [ADR 0002 물리 ERD와 참고 DDL](../decisions/0002-postgresql-persistence-contract.md) · [PlantUML ERD](../diagrams/guardbench-mvp-physical-erd.puml) | #14 |
| TestExecution, SnapshotEvaluation, QualityGateResult는 어떤 Repository가 저장하며 내부 결과를 어떻게 매핑하는가? | [ADR 0003 결과 Aggregate와 write-side Port](../decisions/0003-result-aggregate-and-write-port-boundaries.md) | #14 |
| `FINISHED`와 QualityGateResult는 어떻게 함께 저장·rollback·재호출하는가? | [ADR 0004 최종화 원자성](../decisions/0004-testrun-finalization-atomicity.md) | #14, #18, #19 |
| `TestRunRequested`, `TestExecutionRequested`, `TestExecutionCompleted` v1 JSON·schemaVersion·호환성·DLQ 조건은? | [ADR 0005 v1 메시지 계약](../decisions/0005-async-test-run-execution-contract.md#v1-메시지-계약) | #16, #18, #19 |
| `eventType`은 어느 SQS Queue로 발행되며, Publisher가 Outbox를 어떻게 발행하는가? | [ADR 0005 런타임 역할과 Queue](../decisions/0005-async-test-run-execution-contract.md#런타임-역할과-queue) · [Outbox와 Publisher](../decisions/0005-async-test-run-execution-contract.md#outbox와-publisher) | #14, #16, #18 |
| `outbox_event`의 최소 relation·deduplication key·PENDING/PUBLISHED·`SKIP LOCKED` 계약은? | [ADR 0005 Outbox와 Publisher](../decisions/0005-async-test-run-execution-contract.md#outbox와-publisher) | #14 |
| `test_execution_claim`의 lease·claim token·stale 결과 차단·terminal 결과/완료 Outbox 원자 저장은? | [ADR 0005 TestExecution claim과 결과 저장](../decisions/0005-async-test-run-execution-contract.md#testexecution-claim과-결과-저장) | #14, #18, #19 |
| Candidate materialization, resolution claim, fan-out과 `clientRequestToken`은? | [ADR 0005 작업 단위와 fan-out](../decisions/0005-async-test-run-execution-contract.md#작업-단위와-fan-out) | #17, #18 |
| Provider retry·timeout·visibility·DLQ와 안전한 TestExecution 오류 code는? | [ADR 0005 재시도, timeout, visibility와 DLQ](../decisions/0005-async-test-run-execution-contract.md#재시도-timeout-visibility와-dlq) | #15, #17, #18, #19 |
| Context 간 Persistence/Integration Adapter에서 Java Domain 타입을 어떻게 격리하는가? | [ADR 0006 소비자 소유 Port와 값 기반 계약](../decisions/0006-independent-domain-contract-boundaries.md#소비자-소유-port와-값-기반-계약) | #14, #17, #18 |

## canonical 위치와 중복 금지

| 대상 | canonical 위치 | 이 인덱스의 역할 |
| --- | --- | --- |
| 기존 TestRun·Snapshot·Execution·Evaluation 물리 스키마 | [ADR 0002](../decisions/0002-postgresql-persistence-contract.md)와 [PlantUML ERD](../diagrams/guardbench-mvp-physical-erd.puml) | 링크만 제공한다. `docs/contracts/`로 이동하거나 DDL을 복사하지 않는다. |
| 실제 적용 DDL | `src/main/resources/db/migration/` Flyway SQL | #14 구현 후 문서·ERD와 일치하는지 검증한다. |
| Aggregate와 Repository 소유권 | [ADR 0003](../decisions/0003-result-aggregate-and-write-port-boundaries.md) | 각 Context의 Persistence Model과 Mapper를 어디에 둘지 판단할 때 연결한다. |
| 비동기 실행, Outbox, claim, 메시지, retry | [ADR 0005](../decisions/0005-async-test-run-execution-contract.md) | #14·#16·#17·#18·#19가 필요한 단락으로 이동하게 한다. |
| Context 간 Java 타입 격리 | [ADR 0006](../decisions/0006-independent-domain-contract-boundaries.md) | scalar/code 계약과 Integration Adapter 경계를 확인하게 한다. |

## #14 착수 전 미결정

[Issue #49 미결정 기록](https://github.com/GuardBench/guardbench-backend/issues/49#issuecomment-5404846847)에 따라 다음 물리 계약은 아직 APPROVED source가 없다.

- resolution claim relation의 relation 이름·PK·컬럼·제약·조건부 선점 SQL
- HTTP Idempotency relation의 key·payload 충돌·TestRun/Outbox 연결·제약
- Outbox와 execution claim의 일부 SQL type·CHECK·index
- 위 relation의 물리 ERD cardinality

따라서 #14는 이를 임의 Migration으로 확정하지 않는다. 승인된 결정이 추가되면, 이 인덱스에는 새 canonical 계약 문서만 연결한다.
