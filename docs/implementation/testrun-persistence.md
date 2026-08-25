# TestRun Persistence Adapter 구현 계약 준비

> Status: DRAFT
> Owner: Backend
> Last reviewed: 2026-08-25
> Canonical source: GitHub
> Origin: [GitHub Issue #49](https://github.com/GuardBench/guardbench-backend/issues/49)

이 문서는 Issue #14가 승인된 비동기 실행·영속성 계약을 필요한 범위에서 읽도록 돕는 준비 문서다. 새로운 DB 구조, Java 타입 또는 동작을 결정하지 않는다. `DRAFT`이므로 구현의 단독 근거가 아니며, 각 항목의 승인 근거는 링크한 ADR이 우선한다.

## #14 시작 전 읽기 순서

1. [ADR 0002](../decisions/0002-postgresql-persistence-contract.md): 기존 물리 ERD, JPA/Flyway, ID·시각·FK·CHECK·index 규칙
2. [ADR 0003](../decisions/0003-result-aggregate-and-write-port-boundaries.md): TestExecution·SnapshotEvaluation·QualityGateResult Repository 저장 경계
3. [ADR 0004](../decisions/0004-testrun-finalization-atomicity.md): QualityGateResult와 `FINISHED`의 원자적 저장
4. [ADR 0005](../decisions/0005-async-test-run-execution-contract.md): Outbox, claim, 메시지와 저장/ack 순서
5. [ADR 0006](../decisions/0006-independent-domain-contract-boundaries.md): Context별 Persistence Model과 Java 타입 격리

## #14의 승인된 구현 경계

- Flyway SQL versioned migration으로 기존 `V1__create_guardbench_schema.sql` 뒤에 추가한다. 이미 적용될 수 있는 V1을 수정하지 않는다.
- Domain 객체에는 JPA annotation을 붙이지 않고, 각 소유 Context의 `infrastructure/persistence` Entity/Mapper가 Domain과 명시적으로 변환한다.
- `testrun`은 `TestRun`, `TestCaseSnapshot`, `TestExecution`과 `TestExecutionClaimPort` 구현을 소유한다. `TestExecutionClaimPort`는 Aggregate Repository가 아닌 lease 기간의 중복 호출과 stale 결과 저장을 제어하는 기술 Port다.
- `evaluation`은 `SnapshotEvaluation`과 `QualityGateResult` Persistence Model·Repository 구현을 소유한다. `testrun` Persistence Model을 Java 타입으로 직접 재사용하지 않는다.
- QualityGateResult 저장과 TestRun `FINISHED` 전환은 하나의 PostgreSQL 트랜잭션에서 commit 또는 rollback한다. 이미 완료된 결과를 upsert·재계산·덮어쓰지 않는다.
- 메시지는 DB 상태의 복사본이 아니다. terminal TestExecution과 완료 Outbox는 같은 트랜잭션으로 최초 저장하고, Outbox와 SQS 기술 실패를 TestExecution의 Provider 실패로 변환하지 않는다.

## 승인된 물리 계약

### 기존 결과 스키마

`test_run`, `test_case_snapshot`, `test_execution`, `assertion_result`, `change_result`, `quality_gate_result`의 컬럼·PK/FK·CHECK·index는 [ADR 0002의 참고 DDL](../decisions/0002-postgresql-persistence-contract.md#참고-sql-ddl)과 [물리 ERD](../diagrams/guardbench-mvp-physical-erd.puml)가 기준이다. #14는 이 테이블들을 ADR 0003의 Aggregate/Repository 경계에 매핑한다.

특히 `test_execution`의 PK는 `(snapshot_id, target_type)`이며, 실행 중 상태를 여기에 추가하지 않는다. `quality_gate_result.test_run_id`는 Run당 최대 한 행만 허용하고, `FINISHED`와 Quality Gate의 양방향 존재 불변식은 Application 트랜잭션과 통합 테스트로 보장한다.

### Outbox

ADR 0005가 승인한 최소 relation은 `outbox_event`다.

| 항목 | 승인된 물리 계약 | 근거 |
| --- | --- | --- |
| 식별 | `event_id UUID` PK | ADR 0005 Outbox·v1 메시지 |
| 메시지 | `event_type`, `schema_version`, `payload JSONB` | ADR 0005 Outbox·v1 메시지 |
| 멱등 | `deduplication_key` UNIQUE | ADR 0005 Outbox |
| 발행 상태 | `status`는 `PENDING` 또는 `PUBLISHED` | ADR 0005 Outbox |
| 시각 | `created_at`, nullable `published_at`; 모든 시각은 `TIMESTAMPTZ(6)` | ADR 0005, ADR 0002 ID와 시각 |
| 조회 경로 | `(status, created_at)` index | ADR 0005 Outbox |
| Queue 결정 | destination을 중복 저장하지 않고 `event_type`으로 결정 | ADR 0005 Outbox |

Publisher는 `SELECT ... FOR UPDATE SKIP LOCKED`로 PENDING batch를 가져오고, SQS 발행 성공 항목만 PUBLISHED로 바꾼다. 발행 실패 항목은 동일 `eventId`로 PENDING에 남긴다. `DEAD`, 항목별 attempt, next-attempt와 PUBLISHED cleanup은 MVP 범위가 아니다.

### TestExecution claim

ADR 0005가 승인한 최소 relation은 `test_execution_claim`이다.

| 항목 | 승인된 물리 계약 | 근거 |
| --- | --- | --- |
| 식별 | `(snapshot_id, target_type)` PK | ADR 0005 TestExecution claim, ADR 0002 TestExecution PK |
| claim 값 | `claim_token UUID`, `lease_until`, `attempt_count`, `claimed_at`, `updated_at` | ADR 0005 TestExecution claim |
| ID/Enum/시각 매핑 | `snapshot_id`는 BIGINT, `target_type`은 승인된 target code, 시각은 `TIMESTAMPTZ(6)` | ADR 0002 ID·시각·Enum 규칙 |
| 선점 조건 | claim이 없거나 lease가 만료됐을 때만 새 token으로 선점 | ADR 0005 TestExecution claim |
| 결과 저장 | 현재 token을 재검증하고, 현재 claim token의 최초 terminal 결과와 완료 Outbox만 저장 | ADR 0005 TestExecution claim |

claim은 Bedrock 호출 전의 짧은 트랜잭션으로 획득하고 호출 중 DB connection·row lock을 유지하지 않는다. lease 만료 경계의 Provider 호출은 at-least-once일 수 있지만 만료 Worker의 늦은 결과는 저장·완료 이벤트 생성에 영향을 주지 않는다.

## 승인 근거가 아직 없는 물리 세부

다음은 #14가 임의로 확정하거나 Migration으로 구현하면 안 되는 항목이다. ADR 0005가 요구한 동작은 있으나, Issue #49 완료 조건의 실제 DDL/ERD를 만들기 위한 물리 표현이 승인되어 있지 않다.

| 미결정 | 현재 승인된 사실 | 필요한 결정 |
| --- | --- | --- |
| resolution claim relation | Orchestrator가 `testRunId` 기준 기술적 resolution claim/lease를 획득한다. | relation 이름, PK, token/lease/attempt·시각 컬럼, FK·CHECK·index와 조건부 선점 SQL |
| HTTP idempotency relation | HTTP 요청 멱등성은 Outbox 재발행·Worker claim·결과 PK와 별도 경계다. #16은 idempotency 기록을 저장한다. | relation 이름, key·payload 비교·TestRun/Outbox 연결 방식, 충돌 표현, PK/FK/UNIQUE/index |
| Outbox 미지정 SQL 세부 | 위 최소 컬럼·PK/UNIQUE/index와 상태 의미는 승인됐다. | `event_type`, `schema_version`, `deduplication_key`, `status`의 정확한 SQL type·NOT NULL·CHECK와 payload 검증 범위 |
| execution claim 보강 제약 | PK와 claim/lease 동작은 승인됐다. | `attempt_count`의 초기값·범위 CHECK, snapshot FK 유무와 삭제 정책, lease/시각 CHECK, 필요한 index |
| ERD 확장 | ADR 0002 ERD는 Outbox와 Idempotency를 의도적으로 제외했다. | resolution claim·idempotency 포함 여부와 위 relation의 최종 cardinality 표기 |

이 미결정은 구현 편의를 위한 타입 선택이 아니라 DB 제약·동시성·멱등성 공개 의미에 영향을 준다. 결정이 승인되기 전 #14는 기존 8개 테이블의 Mapper/Repository 준비와 승인된 Outbox·execution claim 요구의 테스트 설계까지만 진행하고, 새 relation Migration은 중단한다.

## #14 검증 체크리스트

결정이 보완된 뒤 #14는 최소한 다음을 통합 테스트로 검증한다.

1. 기존 TestRun·Snapshot·Execution·Evaluation 결과가 ADR 0002의 shape와 ADR 0003의 Repository 저장 단위를 손실 없이 round-trip한다.
2. 같은 `(snapshot_id, target_type)`의 서로 다른 terminal 결과가 기존 결과를 덮어쓰지 않는다.
3. 실행 결과와 `TestExecutionCompleted` Outbox가 함께 commit 또는 rollback한다.
4. 동일 실행에 유효한 claim은 한 Worker만 얻고, 만료 token의 늦은 결과는 저장되지 않는다.
5. QualityGateResult와 TestRun `FINISHED`가 함께 commit되고, 하나의 저장 실패가 둘 다 rollback한다.
6. Outbox Publisher가 PENDING row를 중복 batch로 잠그지 않고, 발행 성공 후 PUBLISHED commit 실패에 따른 재발행을 소비자 멱등성으로 흡수한다.

## 후속 갱신

#14의 승인된 Migration·Entity·index가 확정되면 이 문서를 `APPROVED` 계약으로 갱신하고, 물리 ERD와 실제 Flyway DDL의 일치를 검증한다. #16은 요청 Idempotency·`TestRunRequested` 원자성을, #18은 resolution/execution claim·ack/retry를 각각 이 문서와 연결한다.
