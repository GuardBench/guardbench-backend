# TestRun Persistence 구현 인덱스

> Status: APPROVED
> Owner: Backend
> Scope: GitHub Issue #14
> Last reviewed: 2026-08-27
> Related broad documentation issue: [#49](https://github.com/GuardBench/guardbench-backend/issues/49)

이 문서는 #14에서 구현된 PostgreSQL 물리 Persistence 산출물의 탐색 인덱스다. 새 동작, DB 제약 또는 Context 경계를 결정하지 않으며, 구현 판단은 아래 APPROVED ADR을 따른다.

## 승인 계약

- [ADR 0002: PostgreSQL 영속성 계약과 물리 ERD](../decisions/0002-postgresql-persistence-contract.md)
- [ADR 0003: 실행·평가 결과 Aggregate와 write-side Port 경계](../decisions/0003-result-aggregate-and-write-port-boundaries.md)
- [ADR 0006: 독립 Domain 경계와 Java 타입 격리](../decisions/0006-independent-domain-contract-boundaries.md)
- [ADR 0008: 비동기 TestRun 물리 멱등성·claim·Outbox 계약](../decisions/0008-async-testrun-persistence-contract.md)

## 실제 산출물

| 구분 | 위치 | 내용 |
| --- | --- | --- |
| Core schema | `src/main/resources/db/migration/V1__create_guardbench_schema.sql` | TestRun, Snapshot, Execution, Assertion, Change, Quality Gate 테이블·PK/FK/CHECK·index |
| Async technical schema | `src/main/resources/db/migration/V2__create_async_testrun_technical_tables.sql` | HTTP idempotency, Outbox, resolution/execution claim 물리 계약 |
| ERD | [PlantUML ERD](../diagrams/guardbench-mvp-physical-erd.puml) | V1/V2 관계와 cardinality |
| TestRun write adapters | `testrun/infrastructure/persistence` | TestRun, Snapshot, TestExecution, idempotency, Outbox, claim Adapter |
| Evaluation write adapters | `evaluation/infrastructure/persistence` | SnapshotEvaluation(Assertion + optional Change) 및 QualityGateResult Adapter |
| Evaluation write ports | `evaluation/domain/repository` | Evaluation 소유 local reference VO를 쓰는 write-side Port |
| PostgreSQL integration tests | `src/test/java/com/guardbench/*Persistence*IntegrationTest.java`, `EvaluationPersistenceAdapterIntegrationTest.java` | Flyway schema와 Repository round-trip·제약 검증 |

## 시각 소유권

- Aggregate 생성·수정·lifecycle 시각은 Application이 주입받은 `Clock`으로 결정하고 Domain과 Persistence Adapter가 `Instant`를 보존한다.
- Evaluation의 `SnapshotEvaluation.createdAt`과 `QualityGateResult.createdAt`은 해당 규칙을 따른다.
- idempotency 만료와 resolution/execution claim lease·비교는 여러 Worker 간 동시성 경계이므로 ADR 0008대로 PostgreSQL `clock_timestamp()`을 사용한다.

## 범위 제외

- QualityGateResult 저장과 TestRun `FINISHED` 전환의 Application 트랜잭션 조율은 ADR 0004 및 후속 Worker/finalization 범위다.
- TestRun 조회 HTTP API와 `testrun/application/port/out` Query Port는 #15의 별도 worktree/PR 범위다.
- Queue, retry, DLQ, Worker 계약은 [ADR 0005](../decisions/0005-async-test-run-execution-contract.md)와 [비동기 TestRun 계약 맵](../contracts/README.md)이 소유하며 이 문서로 대체하지 않는다.
- #19(통합·회귀 테스트)는 실제 PostgreSQL에서 접수·Worker 체인·결과 조회를 검증한다. SQS 전송·DLQ와 같은 비동기 인프라 경계는 이 Persistence 인덱스의 범위가 아니다.
