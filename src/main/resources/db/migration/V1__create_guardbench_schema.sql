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

    CONSTRAINT ck_test_suite_name_nonblank CHECK (name ~ '[^[:space:]]'),
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
    CONSTRAINT ck_test_case_name_nonblank CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT ck_test_case_input_nonblank CHECK (input ~ '[^[:space:]]'),
    CONSTRAINT ck_test_case_category_nonblank CHECK (category ~ '[^[:space:]]'),
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
            baseline_guardrail_id ~ '[^[:space:]]'
            AND candidate_guardrail_id ~ '[^[:space:]]'
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
    CONSTRAINT ck_snapshot_name_nonblank CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT ck_snapshot_input_nonblank CHECK (input ~ '[^[:space:]]'),
    CONSTRAINT ck_snapshot_category_nonblank CHECK (category ~ '[^[:space:]]'),
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
