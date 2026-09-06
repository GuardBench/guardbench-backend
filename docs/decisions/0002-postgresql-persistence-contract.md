# 0002. PostgreSQL 영속성 계약과 물리 ERD

> Status: APPROVED
> Owner: Backend
> Last reviewed: 2026-09-07
> Canonical source: GitHub
> Origin: [GitHub Issue #4](https://github.com/GuardBench/guardbench-backend/issues/4)
> Related: [ADR 0010](0010-single-target-test-run-model.md), [ADR 0012](0012-testdefinition-hard-delete-and-historical-identity.md), [ADR 0013](0013-response-behavior-classifier.md)

- ADR Status: ACCEPTED
- Decision date: 2026-08-24
- Related Issue: #4

## Context

GuardBench는 TestSuite, TestCase, TestRun, Snapshot, AI Application 실행 결과, 평가 결과와 비동기 처리 상태를 PostgreSQL에 저장한다.

현재 구현과 순서가 고정된 Flyway migration을 ground truth로 사용한다. 현재 migration history 안에서는 공유 database의 in-place upgrade를 지원하지만, 폐기된 Guardrail 중심 migration history와 데이터의 upgrade compatibility는 보존하지 않는다.

## Decision

### Persistence 기술

- PostgreSQL을 사용한다.
- Aggregate 저장 Adapter는 Spring Data JPA 또는 명시적 JDBC query를 사용한다.
- Domain 객체에는 JPA annotation을 두지 않고 persistence model과 mapper를 분리한다.
- Spring `Page`, `Pageable`, JPA Entity 같은 persistence 타입을 Domain/Application 경계 밖으로 노출하지 않는다.
- Hibernate `ddl-auto`로 스키마를 생성하지 않고 Flyway를 사용한다.

### Migration 기준

현재 schema는 `src/main/resources/db/migration` 아래 migration을 버전 순서대로 적용해 정의한다.

- `V1__create_guardbench_schema.sql`: 현재 GuardBench 기본 schema
- `V2__add_quality_gate_thresholds.sql`: TestRun별 Quality Gate 기준
- `V3__add_test_case_bulk_idempotency.sql`: TestCase 일괄 등록 멱등성

Fresh database는 V1 → V2 → V3를 순서대로 적용한다. 이미 V1 또는 V2까지 적용된 공유 database는 적용되지 않은 다음 migration을 in-place로 적용하며, 적용된 migration 파일은 수정하지 않는다.

이 V1은 현재 모델을 직접 생성하며 다음 과거 스키마를 만들지 않는다.

- Bedrock Guardrail Target / Evaluator 전용 테이블
- Baseline / Candidate 전용 TestRun 컬럼
- Candidate DRAFT materialization 컬럼
- evaluator profile / checks / strictness 컬럼
- 복합 `(snapshot_id, target_type)` execution/claim key

현재 저장소에 존재하는 V1 이후 migration history의 upgrade path는 보존한다. 다만 현재 V1보다 앞선 폐기 schema나 저장소에서 제거된 과거 migration history를 현재 V1으로 변환하는 upgrade path는 제공하지 않는다.

### Test definition

- `test_suite`와 `test_case`는 현재 편집 가능한 테스트 정의를 저장한다.
- `test_case.test_suite_id`는 현재 자산 관계이므로 FK를 유지한다.
- TestCase 삭제는 물리 삭제다.
- TestRun 생성 시 `test_case_snapshot`이 `name`, `input`, `expected_action`, `severity`, `category`를 복제한다.
- `test_case_snapshot.source_test_case_id`와 `test_run.test_suite_id`는 historical identity scalar이며 원본 row에 FK를 두지 않는다.

### TestRun과 Target

- TestRun은 하나의 `target_reference_id`를 가진다.
- `target_reference.target_type`은 현재 `HTTP_ENDPOINT`만 허용한다.
- `http_endpoint_target`은 `endpoint_url`, 필수 `model`, 선택적 `requested_revision`을 저장한다.
- 하나의 TargetReference는 하나의 TestRun에 고정되도록 `test_run.target_reference_id`에 UNIQUE를 둔다.
- TestRun 상세/Regression 조회는 `http_endpoint_target`을 직접 조회하며 provider fallback을 사용하지 않는다.

### Response Behavior Classifier

- `evaluator_reference`는 실행 당시 Response Behavior Classifier의 `provider_code`, `model_id`를 저장한다.
- 사용자가 classifier 설정을 TestRun 요청으로 제출하지 않는다.
- 서버가 현재 배포 configuration을 등록하고 TestRun이 해당 reference를 보존한다.
- 현재 classifier 실행 세부 계약은 ADR 0013을 따른다.

### TestExecution

- Snapshot당 `test_execution`은 최대 한 행이며 PK는 `snapshot_id`다.
- 상태는 `SUCCEEDED | FAILED | TIMED_OUT | NOT_STARTED`다.
- 성공 시 `application_response`와 `evaluator_verdict(ALLOW | BLOCK)`가 존재한다.
- 실패/timeout 시 `error_stage`, `error_code`, `error_message`를 저장한다.
- `error_stage`는 현재 `APPLICATION_TARGET | EVALUATOR`다.
- 실행 시각은 `TIMESTAMPTZ(6)`으로 저장하고 Java에서는 `Instant`를 사용한다.

### Evaluation

- `assertion_result`는 Snapshot의 expected action과 evaluator verdict 비교 결과를 저장한다.
- `quality_gate_result`는 TestRun 최종 Quality Gate 상태와 `assertion_pass_rate`, `execution_success_rate`를 저장한다.
- `NOT_EVALUATED`이면 두 metric은 `NULL`이고 `PASS | FAIL`이면 두 metric이 존재한다.
- 현재 존재하는 `change_result` persistence shape는 평가 저장 경계가 소유하며, Regression API는 완료된 Run의 Snapshot 정의와 저장 verdict를 조회해 비교한다.

### 비동기 처리

- `test_run_idempotency`: HTTP 생성 요청 멱등성
- `test_case_bulk_idempotency`: TestCase 일괄 등록의 요청 fingerprint와 생성 결과
- `test_run_resolution_claim`: TestRun resolution lease
- `test_execution_claim`: Snapshot execution lease
- `outbox_event`: 비동기 이벤트의 transactional outbox

claim lease 및 idempotency 만료처럼 여러 Worker가 공유하는 동시성 시간 비교는 PostgreSQL DB time을 사용한다.

### ID와 시각

- 주요 Aggregate 식별자는 PostgreSQL `BIGINT`를 사용한다.
- sequence allocation increment는 50으로 맞춘다.
- 생성/수정/lifecycle 시각은 Application이 주입된 `Clock`으로 결정하고 persistence가 그대로 저장한다.
- DB trigger를 통한 암묵적 updated-at 갱신은 사용하지 않는다.

### 값과 제약

- 현재 계약의 scalar 값은 정규 컬럼으로 저장한다.
- Enum은 PostgreSQL enum type 대신 `VARCHAR + CHECK`를 사용한다.
- nonblank DB 방어 검증에는 POSIX `[:space:]` 기반 CHECK를 사용한다.
- Outbox payload처럼 구조 자체가 기술 계약인 경우에만 JSONB를 사용한다.

## 물리 테이블

| 테이블 | 역할 |
| --- | --- |
| `test_suite` | TestSuite 현재 정의 |
| `test_case` | TestCase 현재 정의 |
| `target_reference` | HTTP Application Target reference |
| `http_endpoint_target` | HTTP endpoint/model/revision 상세 |
| `evaluator_reference` | Response Behavior Classifier 식별 정보 |
| `test_run` | 실행 수명주기, Target/Classifier reference, 진행률 |
| `test_case_snapshot` | Run 시점 TestCase 정의 snapshot |
| `test_execution` | Snapshot 단일 실행 결과 |
| `assertion_result` | expected action과 evaluator verdict 비교 결과 |
| `change_result` | 평가 persistence shape |
| `quality_gate_result` | TestRun 최종 평가 |
| `test_run_idempotency` | 생성 요청 멱등성 |
| `test_case_bulk_idempotency` | TestCase 일괄 등록 요청 멱등성 |
| `test_run_resolution_claim` | resolution lease |
| `test_execution_claim` | execution lease |
| `outbox_event` | transactional outbox |

편집 가능한 물리 ERD는 [PlantUML ERD](../diagrams/guardbench-mvp-physical-erd.puml)다.

## TestRun lifecycle

- `QUEUED`: 시작/완료 시각과 execution outcome이 없고 processed count는 0이다.
- `PREPARING`: 시작 시각이 있고 완료 시각/outcome은 없다.
- `RUNNING`: 시작 시각이 있고 완료 시각/outcome은 없다.
- `FINISHED`: 완료 시각과 execution outcome이 있으며 processed count가 test case count와 같다.

## Validation

- `PersistenceFoundationIntegrationTest`가 fresh PostgreSQL에 V1 → V2 → V3가 순서대로 적용되는지 검증한다.
- 같은 테스트가 공유 database의 직전 schema인 V2에서 V3로 in-place upgrade되는지 검증한다.
- 현재 schema에는 Guardrail 전용 persistence object가 존재하지 않아야 한다.
- `target_reference.target_type`은 `HTTP_ENDPOINT` 외 값을 허용하지 않는다.
- HTTP Target URL/model DB 제약을 통합 테스트로 검증한다.
- TestRun 상세/Regression persistence integration test는 HTTP Target과 classifier reference 기준으로 검증한다.
- 물리 ERD와 현재 V1 → V2 → V3 적용 결과는 동일한 테이블/관계를 설명해야 한다.
