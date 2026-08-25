# 비동기 TestRun 계약 맵

> Status: DRAFT
> Owner: Backend
> Last reviewed: 2026-08-25
> Canonical source: GitHub
> Origin: [GitHub Issue #49](https://github.com/GuardBench/guardbench-backend/issues/49)

이 문서는 비동기 TestRun 구현의 **결정형 탐색 맵**이다. 각 계약 키에는 구현 결론을 정하는 APPROVED `Primary contract`를 하나만 둔다. `보조 참조`는 함께 읽어야 할 인접 계약이지만 Primary의 소유 범위를 대체하지 않는다.

`DRAFT`인 이 문서는 ADR·ERD·Migration을 복사하거나 새 동작·DB 구조를 정하지 않는다. 구현은 항상 Primary contract를 근거로 하며, Primary와 보조 참조가 충돌하거나 필요한 계약 키가 없으면 Issue에 기록하고 중단한다.

## 결정형 계약 맵

| 계약 키 | Primary contract | 필수 보조 참조 | Primary가 소유하는 결정 | 구현 범위 |
| --- | --- | --- | --- | --- |
| `postgresql-core-schema` | [ADR 0002: PostgreSQL 영속성 계약과 물리 ERD](../decisions/0002-postgresql-persistence-contract.md) | [PlantUML ERD](../diagrams/guardbench-mvp-physical-erd.puml) | 기존 TestRun·Snapshot·Execution·Evaluation 스키마, JPA 분리 모델, Flyway 방식, 공통 FK·시각 규칙 | #14 |
| `result-write-boundary` | [ADR 0003: 결과 Aggregate와 write-side Port](../decisions/0003-result-aggregate-and-write-port-boundaries.md) | [ADR 0006](../decisions/0006-independent-domain-contract-boundaries.md) | TestExecution·SnapshotEvaluation·QualityGateResult의 Aggregate·Repository 소유권 | #14 |
| `testrun-finalization` | [ADR 0004: 최종화 원자성](../decisions/0004-testrun-finalization-atomicity.md) | [ADR 0003](../decisions/0003-result-aggregate-and-write-port-boundaries.md), [ADR 0006](../decisions/0006-independent-domain-contract-boundaries.md) | QualityGateResult 저장과 TestRun `FINISHED` 전환의 원자성·재호출 의미 | #14, #18, #19 |
| `candidate-http-input` | [ADR 0007: Candidate DRAFT 입력](../decisions/0007-testrun-candidate-draft-input.md) | [OpenAPI](../api/openapi.yaml), [API 안내](../api/README.md) | Candidate 요청은 `DRAFT`만 허용하며 numbered version을 받지 않음 | #16 |
| `testrun-idempotency` | [ADR 0008: HTTP Idempotency](../decisions/0008-async-testrun-persistence-contract.md#http-idempotency) | [ADR 0007](../decisions/0007-testrun-candidate-draft-input.md), [ADR 0002](../decisions/0002-postgresql-persistence-contract.md) | global key, normalized-intent SHA-256, 3시간 TTL, DB 논리 만료와 재사용 | #14, #16 |
| `async-message-contract` | [ADR 0005: v1 메시지 계약](../decisions/0005-async-test-run-execution-contract.md#v1-메시지-계약) | [ADR 0008: Outbox](../decisions/0008-async-testrun-persistence-contract.md#outbox) | v1 JSON, schemaVersion, eventType, Queue routing, 메시지 호환성·DLQ 의미 | #16, #18, #19 |
| `outbox-persistence` | [ADR 0008: Outbox](../decisions/0008-async-testrun-persistence-contract.md#outbox) | [ADR 0005: Outbox와 Publisher](../decisions/0005-async-test-run-execution-contract.md#outbox와-publisher), [ADR 0002](../decisions/0002-postgresql-persistence-contract.md) | `outbox_event` DDL, payload 저장, deduplication key, `PENDING/PUBLISHED`, `SKIP LOCKED` | #14, #16, #18 |
| `resolution-flow` | [ADR 0005: 작업 단위와 fan-out](../decisions/0005-async-test-run-execution-contract.md#작업-단위와-fan-out) | [ADR 0007](../decisions/0007-testrun-candidate-draft-input.md), [ADR 0008: Resolution claim](../decisions/0008-async-testrun-persistence-contract.md#resolution-claim) | Candidate materialization 순서, fan-out, `clientRequestToken`, `QUEUED → PREPARING → RUNNING` 흐름 | #17, #18 |
| `resolution-claim-persistence` | [ADR 0008: Resolution claim](../decisions/0008-async-testrun-persistence-contract.md#resolution-claim) | [ADR 0005](../decisions/0005-async-test-run-execution-contract.md#작업-단위와-fan-out) | `test_run_resolution_claim` DDL, 45초 DB lease, 3회 제한, 실패 원자 종결 | #14, #18 |
| `execution-claim-persistence` | [ADR 0008: Execution claim](../decisions/0008-async-testrun-persistence-contract.md#execution-claim) | [ADR 0005: TestExecution claim과 결과 저장](../decisions/0005-async-test-run-execution-contract.md#testexecution-claim과-결과-저장) | `test_execution_claim` DDL, DB lease, stale 결과 차단, terminal 결과·완료 Outbox 원자 저장 | #14, #18, #19 |
| `provider-retry-and-dlq` | [ADR 0005: 재시도·timeout·visibility·DLQ](../decisions/0005-async-test-run-execution-contract.md#재시도-timeout-visibility와-dlq) | [ADR 0008](../decisions/0008-async-testrun-persistence-contract.md) | Provider retry·timeout·visibility·DLQ와 안전한 TestExecution 오류 수렴 | #15, #17, #18, #19 |
| `context-boundary` | [ADR 0006: 소비자 소유 Port와 값 기반 계약](../decisions/0006-independent-domain-contract-boundaries.md#소비자-소유-port와-값-기반-계약) | [ADR 0002](../decisions/0002-postgresql-persistence-contract.md), [ADR 0003](../decisions/0003-result-aggregate-and-write-port-boundaries.md) | Context 간 Java Domain 타입 격리와 Integration Adapter 변환 | #14, #17, #18 |

## 구현 시 적용 규칙

1. 변경하려는 산출물에 해당하는 계약 키를 찾고, 그 행의 **Primary contract부터** 읽는다.
2. 같은 변경이 여러 계약 키에 걸리면 모든 Primary contract를 읽되, 각 ADR은 자기 열에 적힌 결정만 소유한다. 예를 들어 Outbox의 **메시지 형태**는 `async-message-contract`, **저장 형태**는 `outbox-persistence`가 소유한다.
3. 실제 적용 DDL은 `src/main/resources/db/migration/`의 Flyway SQL이지만, 이 파일은 구현 산출물이다. 계약은 ADR 0002·0008과 PlantUML ERD에서 확인하고 Migration·ERD·통합 테스트의 일치를 검증한다.
4. 이 표에 없는 결정, 또는 Primary 간 충돌은 이 DRAFT를 해석해 해결하지 않는다. 관련 Issue에 미결정으로 기록한다.

## 검색 보조어

아래 표현은 계약을 정하는 키가 아니라 탐색 편의를 위한 별칭이다.

| 검색 표현 | 계약 키 |
| --- | --- |
| Candidate 요청, `DRAFT`, numbered version | `candidate-http-input` |
| Idempotency-Key, fingerprint, TTL, key 재사용 | `testrun-idempotency` |
| event JSON, schemaVersion, SQS, eventType | `async-message-contract` |
| Outbox DDL, deduplication, Publisher, `SKIP LOCKED` | `outbox-persistence` |
| materialization, fan-out, `clientRequestToken` | `resolution-flow` |
| resolution lease, 45초, 3회, `NOT_STARTED` | `resolution-claim-persistence` |
| execution lease, stale token, terminal 결과 | `execution-claim-persistence` |
| retry, timeout, visibility, DLQ | `provider-retry-and-dlq` |
