# 0002. PostgreSQL 영속성 계약과 물리 ERD

> Status: DRAFT
> Owner: Backend
> Last reviewed: 2026-08-24
> Canonical source: GitHub
> Origin: [GitHub Issue #4](https://github.com/GuardBench/guardbench-backend/issues/4)
> AI assistance: 이 문서는 LLM의 도움으로 작성되었으며 팀의 검토와 승인이 필요합니다.

- ADR Status: PROPOSED
- Decision date: 2026-08-24
- Related Issue: #4

## Context

GuardBench는 PostgreSQL에 TestSuite, TestCase, TestRun, Snapshot, 실행 결과와 평가 결과를 저장해야 한다. 승인된 API·도메인·평가 계약은 다음 불변식을 요구하지만 구체적인 Persistence 기술과 물리 스키마는 정하지 않았다.

- Domain은 JPA, Spring Data, JDBC 같은 Persistence 기술에 의존하지 않는다.
- `TestSuite`, `TestCase`, `TestRun`, `TestCaseSnapshot`은 각각 별도 Aggregate Root이며 객체 참조 대신 전용 식별자를 사용한다.
- TestCase는 논리 삭제하고 현재 조회와 이후 TestRun에서 제외하지만 기존 Snapshot과 실행·평가 결과에는 삭제를 전파하지 않는다.
- Snapshot은 실행 당시 TestCase의 `name`, `input`, `ExpectedResult.action`, `severity`, `category`를 불변 복제한다.
- TestRun은 `QUEUED → PREPARING → RUNNING → FINISHED` 수명주기, 고정 `testCaseCount`, 처리 진행률과 nullable 종료 결과를 보존한다.
- Candidate DRAFT는 접수 시점이 아니라 `PREPARING`에서 numbered version으로 materialize한다.
- `SUCCEEDED` 실행에만 ActualResult가 있고, Assertion과 Change는 필요한 실행 결과가 있을 때만 생성한다.
- 평가 전 `qualityGate = null`과 종료 후 `NOT_EVALUATED`를 구분하며, `NOT_EVALUATED`의 metrics는 `null`이다.
- Offset Pagination, 필터와 안정적인 다중 정렬을 Spring 타입의 Domain 유출 없이 지원한다.

선행 [ADR 0001](0001-domain-type-ownership-and-aggregate-boundaries.md)은 타입 소유권, Aggregate와 Repository Port 경계를 확정했다. 이 ADR은 그 경계를 바꾸지 않고 Infrastructure의 영속화 표현만 결정한다. 물리 테이블이 존재한다는 사실만으로 `TestExecution` 또는 Evaluation 결과를 Aggregate Root로 승격하거나 새 Repository Port와 Domain ID를 도입하지 않는다.

기존 [Notion ERD](https://app.notion.com/p/3c0eeed6b62d81c78d3bd38dc386a97f)는 2026-08-21의 과거 초안이다. 핵심 관계는 참고할 수 있지만 `updated_at`, TestCase 논리 삭제, Snapshot의 `name`, TestRun lifecycle, Candidate materialization 시점, `NOT_EVALUATED`의 nullable metrics가 현재 계약과 다르므로 물리 계약이나 Migration으로 사용하지 않는다.

## Decision

### Persistence와 Migration

- Aggregate 저장 Adapter는 **Spring Data JPA**를 사용한다.
- Domain 객체와 분리된 Persistence Entity/Model을 각 소유 도메인의 `infrastructure/persistence`에 둔다.
- Infrastructure Mapper가 Domain 객체와 Persistence Model을 명시적으로 변환한다. Domain 클래스에는 JPA annotation을 붙이지 않는다.
- 저장용 Domain Repository Port는 ADR 0001의 네 Port만 유지한다. `TestExecution`과 Evaluation 결과용 Repository Port를 이 결정만으로 추가하지 않는다.
- 실행·평가 조합 조회는 ADR 0001의 `testrun/application/query` 소비자 소유 Projection Port를 `evaluation/infrastructure/query`가 구현한다.
- 복잡한 조회는 custom JPA repository, JPQL 또는 PostgreSQL native query로 구현할 수 있지만 Spring `Page`, `Pageable`, `Sort`, JPA Entity를 Application과 Domain에 노출하지 않는다.
- 스키마 변경은 **Flyway SQL versioned migration**만 사용한다. Hibernate `ddl-auto`는 개발·운영 스키마 생성에 사용하지 않고 실제 구현 시 `validate`를 기본값으로 사용한다.
- 이 ADR은 라이브러리 선택 계약만 확정한다. 실제 의존성, datasource, Flyway 파일, Entity와 Adapter 구현은 후속 Issue 범위다.

### ID와 시각

- 공개 `int64` 계약과 일치하도록 Aggregate 식별자는 PostgreSQL `BIGINT`와 테이블별 sequence를 사용한다.
- JPA sequence allocation과 DB sequence `INCREMENT BY`는 같은 값으로 맞춘다. MVP 권고값은 50이며 ID 연속성을 비즈니스 의미로 사용하지 않는다.
- `TestExecution`, `AssertionResult`, `ChangeResult`, `QualityGateResult`에는 승인되지 않은 Domain ID를 추가하지 않는다.
  - TestExecution PK: `(snapshot_id, target_type)`
  - AssertionResult PK: `snapshot_id`
  - ChangeResult PK: `snapshot_id`
  - QualityGateResult PK: `test_run_id`
- 모든 시각은 PostgreSQL `TIMESTAMPTZ(6)`과 Java `Instant`로 매핑한다.
- Application이 주입된 `Clock`으로 생성·수정·삭제·수명주기 시각을 결정하고 Persistence Adapter가 그대로 저장한다. DB trigger와 암묵적인 last-modified 갱신은 사용하지 않는다.
- `TestSuite.updated_at`은 TestSuite의 `name` 또는 `description`이 실제로 바뀔 때만 변경한다. TestCase 추가·수정·삭제는 별도 Aggregate인 TestSuite의 `updated_at`을 변경하지 않는다.
- TestCase no-op PATCH는 UPDATE를 실행하지 않고 `updated_at`을 유지한다. 논리 삭제 시 `deleted_at`과 `updated_at`을 같은 시각으로 기록한다.
- TestRun의 `updated_at`은 status, target resolution, 진행률, execution outcome이 바뀔 때 갱신한다.

### 값 저장과 논리 삭제

- 현재 계약의 식별자, 문자열, Enum, target, 실행 결과와 Quality Gate metrics는 **정규 컬럼**으로 저장한다.
- 확장 가능성만을 이유로 JSONB를 선제 도입하지 않는다. Provider 원문, effects, outputs, Stack Trace와 임의 configuration은 현재 저장 범위가 아니다.
- 공개 API에 최대 길이가 없는 `name`, `input`, `category`, Guardrail ID는 `TEXT`로 저장해 숨은 길이 제한을 만들지 않는다.
- Enum은 PostgreSQL enum type 대신 `VARCHAR`와 `CHECK`를 사용한다. 값 추가 시 새 Migration으로 CHECK를 변경한다.
- TestCase 논리 삭제는 nullable `deleted_at`으로 표현한다. 활성 행은 `deleted_at IS NULL`이며 일반 조회, 수정과 Snapshot 생성은 이 조건을 반드시 적용한다.
- boolean 삭제 상태는 삭제 시각과 감사 정보를 잃고 별도 시각 컬럼을 다시 요구하므로 사용하지 않는다.

### 기존 ERD와의 차이

| 기존 ERD | 현재 결정 | 변경 근거 |
| --- | --- | --- |
| TestSuite/TestCase에 `created_at`만 존재 | 두 테이블에 `updated_at` 추가 | PATCH 응답과 no-op 수정 시각 계약 |
| TestCase 삭제 컬럼 없음 | nullable `deleted_at`과 활성 행 부분 인덱스 | 논리 삭제와 현재 조회 제외 계약 |
| Snapshot에 `name` 없음 | 다섯 실행 정의 필드를 모두 복제 | 원본 수정·삭제와 무관한 결과 조회 계약 |
| 생성 전에 두 target을 JSONB로 resolve | Candidate 요청 source와 nullable resolved version 분리 | `PREPARING` materialization lifecycle |
| TestRun에 lifecycle·진행률 컬럼 없음 | status, 고정 count, processed count와 세 시각 추가 | Polling·목록·진행률 계약 |
| Execution status 값이 미확정 | 네 터미널 상태와 Actual/Error CHECK | 승인된 실행 결과 계약 |
| Quality Gate metrics가 `NOT NULL JSONB` | `NOT_EVALUATED`이면 전부 null인 정규 컬럼 | 평가 계약의 nullable metrics |
| 입력·Expected·Actual·target이 JSONB 중심 | 승인된 scalar 값을 정규 컬럼으로 저장 | 무결성, filter, sort와 index 요구 |

### 물리 테이블과 관계

편집 가능한 원본과 렌더링 결과는 각각 [PlantUML ERD](../diagrams/guardbench-mvp-physical-erd.puml)와 [PNG ERD](../diagrams/guardbench-mvp-physical-erd.png)다.

| 테이블 | 식별자 | 역할과 주요 규칙 |
| --- | --- | --- |
| `test_suite` | `id` | TestSuite 현재 정의. 이름 중복을 허용하고 활성 TestCase 수는 저장하지 않고 조회 시 집계한다. |
| `test_case` | `id` | TestCase 현재 정의. `deleted_at IS NULL`만 현재 자산으로 취급한다. |
| `test_run` | `id` | 수명주기, 요청·고정 target, 고정 Snapshot 수와 진행률, 종료 결과를 저장한다. |
| `test_case_snapshot` | `id` | Run 시점의 TestCase 다섯 필드를 복제하며 `(test_run_id, source_test_case_id)`가 유일하다. |
| `test_execution` | `(snapshot_id, target_type)` | Snapshot별 BASELINE/CANDIDATE 터미널 실행 결과를 최대 한 행씩 저장한다. |
| `assertion_result` | `snapshot_id` | Candidate ActualResult가 있을 때만 Snapshot당 최대 한 행을 저장한다. |
| `change_result` | `snapshot_id` | 양쪽 ActualResult가 있을 때만 Snapshot당 최대 한 행을 저장한다. |
| `quality_gate_result` | `test_run_id` | 평가가 끝났을 때 Run당 최대 한 행을 저장한다. |

FK 삭제 정책은 모두 `ON DELETE RESTRICT`다. 공개 TestCase 삭제는 물리 DELETE가 아니며 Snapshot에서 원본 TestCase로 향하는 FK를 유지한다. 일반 사용자 동작으로 TestSuite, TestRun, Snapshot과 결과를 물리 삭제하지 않는다. 운영 보존 기간과 물리 삭제 순서는 후속 운영 정책에서 정한다.

TestRun 접수 트랜잭션은 현재 결정의 `test_run`과 `test_case_snapshot` 외에 Outbox와 요청 멱등성 정보도 함께 저장해야 한다. 다만 Outbox·메시지·Worker 및 Idempotency 물리 스키마는 Issue #5의 결정 범위이므로 이 ERD와 참고 DDL에서 의도적으로 제외한다. 따라서 아래 DDL만으로 TestRun 접수 기능 전체가 구현된 것으로 간주하지 않는다.

### TestRun과 nullable 결과

- `QUEUED`: `started_at`, `completed_at`, `candidate_resolved_version`, `execution_outcome`이 `null`이고 처리 건수는 0이다.
- `PREPARING`: `started_at`이 있고 Candidate materialization 전에는 `candidate_resolved_version`이 `null`일 수 있다.
- `RUNNING`: Candidate numbered version이 고정되어 있고 `candidate_resolved_version`이 반드시 존재한다.
- `FINISHED`: `completed_at`, `execution_outcome`이 있고 모든 Snapshot이 터미널 처리되어 `processed_test_case_count = test_case_count`다.
- Candidate materialization 실패로 `FINISHED / ERROR`가 되면 `candidate_resolved_version`은 `null`일 수 있다.
- `SUCCEEDED` TestExecution은 `actual_action`이 있고 오류가 없다. 그 밖의 상태는 ActualResult가 없다.
- `FAILED`와 `TIMED_OUT`의 안전한 오류는 code와 message가 함께 있거나 함께 없다. `NOT_STARTED`에는 ActualResult와 오류, 실행 시각이 없다.
- `quality_gate_result` 행이 없으면 평가 전이다. 행의 status가 `NOT_EVALUATED`이면 모든 metric은 `null`이고 `PASS` 또는 `FAIL`이면 모든 metric이 존재한다.

### 인덱스와 Offset Pagination

다음 인덱스를 MVP의 주요 조회 경로로 확정한다.

- TestSuite 기본 목록: `(updated_at DESC, id DESC)`
- 활성 TestCase 기본 목록: `(test_suite_id, created_at, id) WHERE deleted_at IS NULL`
- 활성 TestCase Enum filter: Suite와 `category`, `expected_action`, `severity`별 부분 인덱스
- TestRun 기본 목록: `(created_at DESC, id DESC)`
- TestSuite별 TestRun: `(test_suite_id, created_at DESC, id DESC)`
- TestRun 상태·outcome filter: `(status, created_at DESC, id DESC)`, `(execution_outcome, created_at DESC, id DESC)`
- Snapshot 결과 기본 목록: `(test_run_id, id)`
- Snapshot 결과 Enum filter: Run과 `category`, `expected_action`, `severity`별 인덱스
- Quality Gate 상태 filter: `(gate_status, test_run_id)`

`name`과 `input`의 대소문자 무시 부분 검색은 `lower(column) LIKE '%...%'`로 정확성을 먼저 구현한다. 선행 wildcard 때문에 일반 B-tree가 이 조회를 가속하지 못하므로 `pg_trgm` extension과 GIN index는 데이터 규모와 운영 권한이 확인된 후 별도 승인한다.

TestSuite의 `testCaseCount`는 활성 TestCase를 집계한 읽기 Projection이다. 별도 Aggregate인 TestSuite에 counter를 중복 저장하지 않는다. `testCaseCount` 정렬·필터는 Infrastructure query가 집계한 전체 결과에 적용한 다음 Offset Pagination한다.

Presentation은 외부 `page`, `size`, `sort`를 검증해 프로젝트 자체 조회 조건으로 변환한다. Application Query Port는 요청 page와 size, 허용된 scalar sort/filter만 표현하며 Spring 타입을 사용하지 않는다. Infrastructure가 `(page - 1) * size`를 안전하게 계산하고 전체 filter·sort 이후 `LIMIT/OFFSET`과 별도 count query를 수행한다.

### 참고 SQL DDL

다음 SQL은 결정 내용을 사람이 검토하기 위한 **참고 DDL**이다. Flyway Migration이 아니며 이 Issue에서 실행하지 않는다. 후속 구현 Issue는 이 계약을 기준으로 실제 Migration을 만들고 PostgreSQL 통합 테스트로 검증해야 한다.

```sql
CREATE SEQUENCE test_suite_id_seq AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE test_case_id_seq AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE test_run_id_seq AS BIGINT START WITH 1 INCREMENT BY 50;
CREATE SEQUENCE test_case_snapshot_id_seq AS BIGINT START WITH 1 INCREMENT BY 50;

CREATE TABLE test_suite (
    id          BIGINT PRIMARY KEY DEFAULT nextval('test_suite_id_seq'),
    name        TEXT NOT NULL,
    description TEXT,
    created_at  TIMESTAMPTZ(6) NOT NULL,
    updated_at  TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT ck_test_suite_name_nonblank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_test_suite_time_order CHECK (updated_at >= created_at)
);

ALTER SEQUENCE test_suite_id_seq OWNED BY test_suite.id;

CREATE INDEX idx_test_suite_updated
    ON test_suite(updated_at DESC, id DESC);

CREATE TABLE test_case (
    id              BIGINT PRIMARY KEY DEFAULT nextval('test_case_id_seq'),
    test_suite_id   BIGINT NOT NULL,
    name            TEXT NOT NULL,
    input           TEXT NOT NULL,
    expected_action VARCHAR(16) NOT NULL,
    severity        VARCHAR(16) NOT NULL,
    category        TEXT NOT NULL,
    created_at      TIMESTAMPTZ(6) NOT NULL,
    updated_at      TIMESTAMPTZ(6) NOT NULL,
    deleted_at      TIMESTAMPTZ(6),

    CONSTRAINT fk_test_case_suite
        FOREIGN KEY (test_suite_id) REFERENCES test_suite(id) ON DELETE RESTRICT,
    CONSTRAINT ck_test_case_name_nonblank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_test_case_input_nonblank CHECK (btrim(input) <> ''),
    CONSTRAINT ck_test_case_category_nonblank CHECK (btrim(category) <> ''),
    CONSTRAINT ck_test_case_expected_action CHECK (expected_action IN ('ALLOW', 'BLOCK')),
    CONSTRAINT ck_test_case_severity
        CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT ck_test_case_time_order
        CHECK (
            updated_at >= created_at
            AND (deleted_at IS NULL OR deleted_at >= created_at)
        )
);

ALTER SEQUENCE test_case_id_seq OWNED BY test_case.id;

CREATE INDEX idx_test_case_active_suite_created
    ON test_case(test_suite_id, created_at, id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_test_case_active_suite_category
    ON test_case(test_suite_id, category, created_at, id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_test_case_active_suite_expected_action
    ON test_case(test_suite_id, expected_action, created_at, id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_test_case_active_suite_severity
    ON test_case(test_suite_id, severity, created_at, id)
    WHERE deleted_at IS NULL;

CREATE TABLE test_run (
    id                        BIGINT PRIMARY KEY DEFAULT nextval('test_run_id_seq'),
    test_suite_id             BIGINT NOT NULL,
    status                    VARCHAR(16) NOT NULL,
    test_case_count           INTEGER NOT NULL,
    processed_test_case_count INTEGER NOT NULL DEFAULT 0,
    baseline_guardrail_id     TEXT NOT NULL,
    baseline_version          TEXT NOT NULL,
    candidate_guardrail_id    TEXT NOT NULL,
    candidate_requested_source VARCHAR(16) NOT NULL,
    candidate_resolved_version TEXT,
    execution_outcome         VARCHAR(16),
    created_at                TIMESTAMPTZ(6) NOT NULL,
    started_at                TIMESTAMPTZ(6),
    completed_at              TIMESTAMPTZ(6),
    updated_at                TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_test_run_suite
        FOREIGN KEY (test_suite_id) REFERENCES test_suite(id) ON DELETE RESTRICT,
    CONSTRAINT ck_test_run_status
        CHECK (status IN ('QUEUED', 'PREPARING', 'RUNNING', 'FINISHED')),
    CONSTRAINT ck_test_run_count
        CHECK (
            test_case_count > 0
            AND processed_test_case_count BETWEEN 0 AND test_case_count
        ),
    CONSTRAINT ck_test_run_guardrail_ids
        CHECK (
            btrim(baseline_guardrail_id) <> ''
            AND btrim(candidate_guardrail_id) <> ''
            AND baseline_guardrail_id = candidate_guardrail_id
        ),
    CONSTRAINT ck_test_run_versions
        CHECK (
            baseline_version ~ '^[0-9]+$'
            AND (
                candidate_resolved_version IS NULL
                OR candidate_resolved_version ~ '^[0-9]+$'
            )
        ),
    CONSTRAINT ck_test_run_candidate_source
        CHECK (candidate_requested_source = 'DRAFT'),
    CONSTRAINT ck_test_run_execution_outcome
        CHECK (
            execution_outcome IS NULL
            OR execution_outcome IN ('COMPLETED', 'INCOMPLETE', 'ERROR')
        ),
    CONSTRAINT ck_test_run_time_order
        CHECK (
            updated_at >= created_at
            AND (started_at IS NULL OR started_at >= created_at)
            AND (completed_at IS NULL OR completed_at >= started_at)
        ),
    CONSTRAINT ck_test_run_lifecycle
        CHECK (
            (
                status = 'QUEUED'
                AND started_at IS NULL
                AND completed_at IS NULL
                AND candidate_resolved_version IS NULL
                AND execution_outcome IS NULL
                AND processed_test_case_count = 0
            )
            OR (
                status = 'PREPARING'
                AND started_at IS NOT NULL
                AND completed_at IS NULL
                AND execution_outcome IS NULL
            )
            OR (
                status = 'RUNNING'
                AND started_at IS NOT NULL
                AND completed_at IS NULL
                AND candidate_resolved_version IS NOT NULL
                AND execution_outcome IS NULL
            )
            OR (
                status = 'FINISHED'
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL
                AND execution_outcome IS NOT NULL
                AND processed_test_case_count = test_case_count
                AND (
                    candidate_resolved_version IS NOT NULL
                    OR execution_outcome = 'ERROR'
                )
            )
        )
);

ALTER SEQUENCE test_run_id_seq OWNED BY test_run.id;

CREATE INDEX idx_test_run_created
    ON test_run(created_at DESC, id DESC);

CREATE INDEX idx_test_run_suite_created
    ON test_run(test_suite_id, created_at DESC, id DESC);

CREATE INDEX idx_test_run_status_created
    ON test_run(status, created_at DESC, id DESC);

CREATE INDEX idx_test_run_outcome_created
    ON test_run(execution_outcome, created_at DESC, id DESC)
    WHERE execution_outcome IS NOT NULL;

CREATE TABLE test_case_snapshot (
    id                  BIGINT PRIMARY KEY DEFAULT nextval('test_case_snapshot_id_seq'),
    test_run_id         BIGINT NOT NULL,
    source_test_case_id BIGINT NOT NULL,
    name                TEXT NOT NULL,
    input               TEXT NOT NULL,
    expected_action     VARCHAR(16) NOT NULL,
    severity            VARCHAR(16) NOT NULL,
    category            TEXT NOT NULL,
    created_at          TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_snapshot_test_run
        FOREIGN KEY (test_run_id) REFERENCES test_run(id) ON DELETE RESTRICT,
    CONSTRAINT fk_snapshot_source_test_case
        FOREIGN KEY (source_test_case_id) REFERENCES test_case(id) ON DELETE RESTRICT,
    CONSTRAINT uk_snapshot_run_source_test_case
        UNIQUE (test_run_id, source_test_case_id),
    CONSTRAINT ck_snapshot_name_nonblank CHECK (btrim(name) <> ''),
    CONSTRAINT ck_snapshot_input_nonblank CHECK (btrim(input) <> ''),
    CONSTRAINT ck_snapshot_category_nonblank CHECK (btrim(category) <> ''),
    CONSTRAINT ck_snapshot_expected_action
        CHECK (expected_action IN ('ALLOW', 'BLOCK')),
    CONSTRAINT ck_snapshot_severity
        CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'))
);

ALTER SEQUENCE test_case_snapshot_id_seq OWNED BY test_case_snapshot.id;

CREATE INDEX idx_snapshot_run_id
    ON test_case_snapshot(test_run_id, id);

CREATE INDEX idx_snapshot_source_test_case_id
    ON test_case_snapshot(source_test_case_id);

CREATE INDEX idx_snapshot_run_category
    ON test_case_snapshot(test_run_id, category, id);

CREATE INDEX idx_snapshot_run_expected_action
    ON test_case_snapshot(test_run_id, expected_action, id);

CREATE INDEX idx_snapshot_run_severity
    ON test_case_snapshot(test_run_id, severity, id);

CREATE TABLE test_execution (
    snapshot_id   BIGINT NOT NULL,
    target_type   VARCHAR(16) NOT NULL,
    result_status VARCHAR(16) NOT NULL,
    actual_action VARCHAR(16),
    error_code    TEXT,
    error_message TEXT,
    started_at    TIMESTAMPTZ(6),
    completed_at  TIMESTAMPTZ(6),

    CONSTRAINT pk_test_execution PRIMARY KEY (snapshot_id, target_type),
    CONSTRAINT fk_execution_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES test_case_snapshot(id) ON DELETE RESTRICT,
    CONSTRAINT ck_execution_target_type
        CHECK (target_type IN ('BASELINE', 'CANDIDATE')),
    CONSTRAINT ck_execution_result_status
        CHECK (result_status IN ('SUCCEEDED', 'FAILED', 'TIMED_OUT', 'NOT_STARTED')),
    CONSTRAINT ck_execution_actual_action
        CHECK (actual_action IS NULL OR actual_action IN ('ALLOW', 'BLOCK')),
    CONSTRAINT ck_execution_error_pair
        CHECK (
            (error_code IS NULL AND error_message IS NULL)
            OR (error_code IS NOT NULL AND error_message IS NOT NULL)
        ),
    CONSTRAINT ck_execution_result_shape
        CHECK (
            (
                result_status = 'SUCCEEDED'
                AND actual_action IS NOT NULL
                AND error_code IS NULL
                AND error_message IS NULL
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL
            )
            OR (
                result_status IN ('FAILED', 'TIMED_OUT')
                AND actual_action IS NULL
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL
            )
            OR (
                result_status = 'NOT_STARTED'
                AND actual_action IS NULL
                AND error_code IS NULL
                AND error_message IS NULL
                AND started_at IS NULL
                AND completed_at IS NULL
            )
        ),
    CONSTRAINT ck_execution_time_order
        CHECK (completed_at IS NULL OR completed_at >= started_at)
);

CREATE TABLE assertion_result (
    snapshot_id      BIGINT PRIMARY KEY,
    assertion_status VARCHAR(16) NOT NULL,
    created_at       TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_assertion_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES test_case_snapshot(id) ON DELETE RESTRICT,
    CONSTRAINT ck_assertion_status
        CHECK (assertion_status IN ('PASS', 'FAIL'))
);

CREATE TABLE change_result (
    snapshot_id          BIGINT PRIMARY KEY,
    comparability_status VARCHAR(32) NOT NULL,
    change_type          VARCHAR(64),
    created_at           TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_change_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES test_case_snapshot(id) ON DELETE RESTRICT,
    CONSTRAINT ck_change_comparability
        CHECK (comparability_status IN ('COMPARABLE', 'NOT_COMPARABLE')),
    CONSTRAINT ck_change_type
        CHECK (
            change_type IS NULL
            OR change_type IN (
                'NO_CHANGE',
                'SECURITY_REGRESSION',
                'USABILITY_REGRESSION',
                'IMPROVEMENT',
                'POLICY_BEHAVIOR_CHANGED'
            )
        ),
    CONSTRAINT ck_change_result_shape
        CHECK (
            (comparability_status = 'COMPARABLE' AND change_type IS NOT NULL)
            OR (comparability_status = 'NOT_COMPARABLE' AND change_type IS NULL)
        )
);

CREATE TABLE quality_gate_result (
    test_run_id                      BIGINT PRIMARY KEY,
    gate_status                      VARCHAR(32) NOT NULL,
    candidate_assertion_pass_rate    DOUBLE PRECISION,
    security_regression_count        INTEGER,
    security_regression_rate         DOUBLE PRECISION,
    usability_regression_rate        DOUBLE PRECISION,
    test_execution_success_rate      DOUBLE PRECISION,
    created_at                       TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_quality_gate_test_run
        FOREIGN KEY (test_run_id) REFERENCES test_run(id) ON DELETE RESTRICT,
    CONSTRAINT ck_quality_gate_status
        CHECK (gate_status IN ('PASS', 'FAIL', 'NOT_EVALUATED')),
    CONSTRAINT ck_quality_gate_metric_ranges
        CHECK (
            (candidate_assertion_pass_rate IS NULL
                OR candidate_assertion_pass_rate BETWEEN 0.0 AND 1.0)
            AND (security_regression_count IS NULL OR security_regression_count >= 0)
            AND (security_regression_rate IS NULL
                OR security_regression_rate BETWEEN 0.0 AND 1.0)
            AND (usability_regression_rate IS NULL
                OR usability_regression_rate BETWEEN 0.0 AND 1.0)
            AND (test_execution_success_rate IS NULL
                OR test_execution_success_rate BETWEEN 0.0 AND 1.0)
        ),
    CONSTRAINT ck_quality_gate_result_shape
        CHECK (
            (
                gate_status = 'NOT_EVALUATED'
                AND candidate_assertion_pass_rate IS NULL
                AND security_regression_count IS NULL
                AND security_regression_rate IS NULL
                AND usability_regression_rate IS NULL
                AND test_execution_success_rate IS NULL
            )
            OR (
                gate_status IN ('PASS', 'FAIL')
                AND candidate_assertion_pass_rate IS NOT NULL
                AND security_regression_count IS NOT NULL
                AND security_regression_rate IS NOT NULL
                AND usability_regression_rate IS NOT NULL
                AND test_execution_success_rate IS NOT NULL
            )
        )
);

CREATE INDEX idx_quality_gate_status_run
    ON quality_gate_result(gate_status, test_run_id);
```

## Alternatives

### Persistence 접근

| 선택지 | 구현 복잡도 | Domain 격리 | 조회 통제 | MVP 판단 |
| --- | --- | --- | --- | --- |
| Spring Data JPA + 별도 Persistence Model | CRUD와 트랜잭션 반복 코드가 적음 | 높음 | custom query로 보완 가능 | 선택 |
| Spring Data JPA + Domain 직접 mapping | 초기 클래스 수가 적음 | JPA annotation과 lazy-loading이 Domain에 침투 | 보통 | 기각 |
| Spring Data JDBC 또는 JdbcTemplate 중심 | SQL 통제가 명시적 | 별도 mapping으로 높게 유지 가능 | 높음 | 8개 테이블 매핑과 CRUD 반복 코드가 MVP에 큼 |

JPA는 Infrastructure 구현 도구일 뿐 Domain 모델과 Aggregate 경계를 결정하지 않는다. 읽기 Projection에 필요한 SQL이 복잡해져도 Domain을 JPA Entity로 바꾸지 않고 custom query로 해결한다.

### Migration

| 선택지 | 장점 | 비용과 판단 |
| --- | --- | --- |
| Flyway SQL | PostgreSQL DDL을 그대로 검토하고 version 순서를 단순하게 관리 | 선택 |
| Liquibase | XML/YAML/JSON 모델과 rollback 기능이 풍부함 | 단일 PostgreSQL MVP에는 도구·changelog 복잡도가 큼 |
| 미도입 또는 Hibernate 자동 DDL | 초기 설정이 적음 | 환경별 schema drift와 변경 이력 부재로 기각 |

### ID

| 선택지 | 장점 | 비용과 판단 |
| --- | --- | --- |
| 테이블별 BIGINT sequence | API int64와 일치하고 다건 저장 allocation을 제어할 수 있음 | 선택. gap을 허용함 |
| identity/BIGSERIAL | DDL이 단순함 | 다건 insert 최적화 제어와 명시성이 sequence보다 낮음 |
| UUID/ULID 등 애플리케이션 ID | DB 왕복 없이 생성 가능 | 공개 int64 계약과 맞지 않고 타입 변환이 추가되어 기각 |

### 논리 삭제와 구조화 값

| 선택지 | 장점 | 비용과 판단 |
| --- | --- | --- |
| nullable `deleted_at` | 활성 조건과 삭제 시각을 함께 표현 | 모든 현재 조회에 조건 필요. 선택 |
| boolean `deleted` | 조건이 단순함 | 삭제 시각을 잃고 별도 컬럼이 필요해 기각 |
| 계약 필드 정규 컬럼 | CHECK, filter, sort, index와 nullable 불변식이 명확함 | 필드 추가에 Migration 필요. 선택 |
| JSONB 중심 | 확장이 빠름 | 현재 고정 계약의 무결성과 조회가 약해져 기각 |
| 혼합 | 확정 필드와 확장 payload를 분리 가능 | 현재 승인된 확장 payload가 없으므로 선제 도입하지 않음 |

## Consequences

장점은 다음과 같다.

- Domain은 Persistence 기술과 객체 lifecycle에서 격리된다.
- TestCase 논리 삭제 후에도 Snapshot과 결과 FK가 유지된다.
- Snapshot과 target이 실행 시점 값으로 보존되고 현재 TestCase 변경과 분리된다.
- lifecycle, 실행 실패, 평가 전, `NOT_EVALUATED`와 nullable metrics를 손실 없이 표현한다.
- result 테이블의 공유 PK가 cardinality를 직접 강제하고 중복 `test_run_id`와 execution ID의 교차 참조 오류를 없앤다.
- PostgreSQL CHECK와 부분 인덱스로 핵심 불변식과 기본 조회를 검증할 수 있다.

비용과 위험은 다음과 같다.

- Domain과 Persistence Model 사이 Mapper 코드가 필요하다.
- Enum 추가, 새 filter와 JSON 구조 도입은 Migration과 ADR 검토를 요구한다.
- TestSuite `testCaseCount` 집계 정렬은 데이터 규모가 커지면 비용이 증가할 수 있다. 측정 없이 중복 counter를 선제 도입하지 않는다.
- `ILIKE '%...%'` 검색은 B-tree로 가속되지 않는다. 필요 시 PostgreSQL extension과 운영 권한을 별도로 검토해야 한다.
- JPA sequence allocation 설정과 DB sequence 증가값이 다르면 식별자 생성 오류가 생길 수 있으므로 통합 테스트가 필요하다.
- 참고 DDL의 cross-row/cross-table 불변식, 예를 들어 Assertion은 Candidate 성공 시에만 생성되고 Quality Gate는 FINISHED Run에만 생성된다는 규칙은 Application 트랜잭션과 통합 테스트로 보완한다.
- Outbox와 Idempotency 테이블이 제외되어 있으므로 후속 #5 결정 없이 비동기 접수 구현을 완료할 수 없다.

이 결정을 되돌리려면 새 ADR로 Persistence 접근이나 스키마 표현을 supersede하고 새 Flyway migration으로 roll-forward한다. 이미 적용된 versioned migration을 수정하거나 운영 DB를 Hibernate 자동 DDL로 역변경하지 않는다.

## Validation

1. ADR 0001의 네 Aggregate Root와 Repository Port 경계를 유지하고 새 Domain ID나 Repository를 선제 도입하지 않았는지 확인한다.
2. `docs/domain/core-model.md`, `docs/domain/evaluation-contract.md`, `docs/api/README.md`, `docs/api/openapi.yaml`의 필드, Enum, nullable 규칙을 테이블·CHECK와 대조한다.
3. TestCase를 논리 삭제해도 Snapshot의 원본 FK와 실행·평가 결과가 유지되는지 검토한다.
4. Snapshot이 `name`, `input`, `expectedAction`, `severity`, `category`를 모두 보존하며 Run 안에서 원본 TestCase당 하나인지 확인한다.
5. `QUEUED`, Candidate materialization 실패, 정상 `RUNNING`, `FINISHED / INCOMPLETE`, `NOT_EVALUATED` 예시 행이 CHECK를 만족하는지 검토한다.
6. 승인된 목록 filter·sort와 기본 정렬마다 실행 가능한 query와 주요 인덱스 경로가 있는지 검토한다.
7. PlantUML 원본, PNG 관계와 참고 DDL의 PK·FK·cardinality가 일치하는지 확인한다.
8. 문서와 다이어그램 외 production/test dependency, datasource, Migration, Entity, Repository Adapter가 변경되지 않았는지 확인한다.
