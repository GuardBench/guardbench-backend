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

    CONSTRAINT fk_test_case_suite
        FOREIGN KEY (test_suite_id) REFERENCES test_suite(id) ON DELETE RESTRICT,
    CONSTRAINT ck_test_case_name_nonblank CHECK (name ~ '[^[:space:]]'),
    CONSTRAINT ck_test_case_input_nonblank CHECK (input ~ '[^[:space:]]'),
    CONSTRAINT ck_test_case_category_nonblank CHECK (category ~ '[^[:space:]]'),
    CONSTRAINT ck_test_case_expected_action CHECK (expected_action IN ('ALLOW', 'BLOCK')),
    CONSTRAINT ck_test_case_severity
        CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW'))
);

ALTER SEQUENCE test_case_id_seq OWNED BY test_case.id;

CREATE TABLE target_reference (
    reference_id TEXT PRIMARY KEY,
    target_type  VARCHAR(32) NOT NULL,

    CONSTRAINT ck_target_reference_id_nonblank
        CHECK (reference_id ~ '[^[:space:]]'),
    CONSTRAINT ck_target_reference_type
        CHECK (target_type = 'HTTP_ENDPOINT')
);

CREATE TABLE http_endpoint_target (
    reference_id      TEXT PRIMARY KEY,
    endpoint_url      TEXT NOT NULL,
    model             TEXT NOT NULL,
    requested_revision TEXT,

    CONSTRAINT fk_http_endpoint_target_reference
        FOREIGN KEY (reference_id) REFERENCES target_reference(reference_id) ON DELETE RESTRICT,
    CONSTRAINT ck_http_endpoint_url_nonblank CHECK (endpoint_url ~ '[^[:space:]]'),
    CONSTRAINT ck_http_endpoint_url_scheme
        CHECK (endpoint_url ~* '^https?://[^/?#[:space:]]+'),
    CONSTRAINT ck_http_endpoint_target_model_nonblank
        CHECK (model ~ '[^[:space:]]'),
    CONSTRAINT ck_http_endpoint_target_revision_nonblank
        CHECK (requested_revision IS NULL OR requested_revision ~ '[^[:space:]]')
);

CREATE TABLE evaluator_reference (
    reference_id  TEXT PRIMARY KEY,
    provider_code VARCHAR(32) NOT NULL,
    model_id      TEXT NOT NULL,

    CONSTRAINT ck_evaluator_reference_provider_code_nonblank
        CHECK (provider_code ~ '[^[:space:]]'),
    CONSTRAINT ck_evaluator_reference_model_id_nonblank
        CHECK (model_id ~ '[^[:space:]]')
);

CREATE TABLE test_run (
    id                        BIGINT PRIMARY KEY DEFAULT nextval('test_run_id_seq'),
    test_suite_id             BIGINT NOT NULL,
    status                    VARCHAR(16) NOT NULL,
    test_case_count           INTEGER NOT NULL,
    processed_test_case_count INTEGER NOT NULL DEFAULT 0,
    target_reference_id       TEXT NOT NULL UNIQUE,
    evaluator_reference_id    TEXT NOT NULL,
    assertion_pass_rate_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.95,
    execution_success_rate_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.95,
    execution_outcome         VARCHAR(16),
    created_at                TIMESTAMPTZ(6) NOT NULL,
    started_at                TIMESTAMPTZ(6),
    completed_at              TIMESTAMPTZ(6),
    updated_at                TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_test_run_target_reference
        FOREIGN KEY (target_reference_id) REFERENCES target_reference(reference_id) ON DELETE RESTRICT,
    CONSTRAINT fk_test_run_evaluator_reference
        FOREIGN KEY (evaluator_reference_id) REFERENCES evaluator_reference(reference_id) ON DELETE RESTRICT,
    CONSTRAINT ck_test_run_status
        CHECK (status IN ('QUEUED', 'PREPARING', 'RUNNING', 'FINISHED')),
    CONSTRAINT ck_test_run_count
        CHECK (
            test_case_count > 0
            AND processed_test_case_count BETWEEN 0 AND test_case_count
        ),
    CONSTRAINT ck_test_run_quality_gate_thresholds
        CHECK (
            assertion_pass_rate_threshold BETWEEN 0.0 AND 1.0
            AND execution_success_rate_threshold BETWEEN 0.0 AND 1.0
        ),
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
                AND execution_outcome IS NULL
            )
            OR (
                status = 'FINISHED'
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL
                AND execution_outcome IS NOT NULL
                AND processed_test_case_count = test_case_count
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
    snapshot_id          BIGINT PRIMARY KEY,
    result_status        VARCHAR(16) NOT NULL,
    application_response TEXT,
    evaluator_verdict    VARCHAR(16),
    error_stage          VARCHAR(32),
    error_code           TEXT,
    error_message        TEXT,
    started_at           TIMESTAMPTZ(6),
    completed_at         TIMESTAMPTZ(6),

    CONSTRAINT fk_execution_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES test_case_snapshot(id) ON DELETE RESTRICT,
    CONSTRAINT ck_execution_result_status
        CHECK (result_status IN ('SUCCEEDED', 'FAILED', 'TIMED_OUT', 'NOT_STARTED')),
    CONSTRAINT ck_execution_application_response
        CHECK (application_response IS NULL OR application_response ~ '[^[:space:]]'),
    CONSTRAINT ck_execution_evaluator_verdict
        CHECK (evaluator_verdict IS NULL OR evaluator_verdict IN ('ALLOW', 'BLOCK')),
    CONSTRAINT ck_execution_error_stage
        CHECK (error_stage IS NULL OR error_stage IN ('APPLICATION_TARGET', 'EVALUATOR')),
    CONSTRAINT ck_execution_error_pair
        CHECK (
            (error_code IS NULL AND error_message IS NULL)
            OR (error_code IS NOT NULL AND error_message IS NOT NULL)
        ),
    CONSTRAINT ck_execution_result_shape
        CHECK (
            (
                result_status = 'SUCCEEDED'
                AND application_response IS NOT NULL
                AND evaluator_verdict IS NOT NULL
                AND error_stage IS NULL
                AND error_code IS NULL
                AND error_message IS NULL
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL
            )
            OR (
                result_status IN ('FAILED', 'TIMED_OUT')
                AND evaluator_verdict IS NULL
                AND error_stage IS NOT NULL
                AND error_code IS NOT NULL
                AND error_message IS NOT NULL
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL
            )
            OR (
                result_status = 'NOT_STARTED'
                AND application_response IS NULL
                AND evaluator_verdict IS NULL
                AND error_code IS NULL
                AND error_message IS NULL
                AND error_stage IS NULL
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
    test_run_id            BIGINT PRIMARY KEY,
    gate_status            VARCHAR(32) NOT NULL,
    assertion_pass_rate    DOUBLE PRECISION,
    assertion_pass_rate_threshold DOUBLE PRECISION,
    assertion_passed       BOOLEAN,
    execution_success_rate DOUBLE PRECISION,
    execution_success_rate_threshold DOUBLE PRECISION,
    execution_passed       BOOLEAN,
    created_at             TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_quality_gate_test_run
        FOREIGN KEY (test_run_id) REFERENCES test_run(id) ON DELETE RESTRICT,
    CONSTRAINT ck_quality_gate_status
        CHECK (gate_status IN ('PASS', 'FAIL', 'NOT_EVALUATED')),
    CONSTRAINT ck_quality_gate_metric_ranges
        CHECK (
            (assertion_pass_rate IS NULL OR assertion_pass_rate BETWEEN 0.0 AND 1.0)
            AND (assertion_pass_rate_threshold IS NULL OR assertion_pass_rate_threshold BETWEEN 0.0 AND 1.0)
            AND (execution_success_rate IS NULL OR execution_success_rate BETWEEN 0.0 AND 1.0)
            AND (execution_success_rate_threshold IS NULL OR execution_success_rate_threshold BETWEEN 0.0 AND 1.0)
        ),
    CONSTRAINT ck_quality_gate_result_shape
        CHECK (
            (
                gate_status = 'NOT_EVALUATED'
                AND assertion_pass_rate IS NULL
                AND assertion_pass_rate_threshold IS NULL
                AND assertion_passed IS NULL
                AND execution_success_rate IS NULL
                AND execution_success_rate_threshold IS NULL
                AND execution_passed IS NULL
            )
            OR (
                gate_status IN ('PASS', 'FAIL')
                AND assertion_pass_rate IS NOT NULL
                AND assertion_pass_rate_threshold IS NOT NULL
                AND assertion_passed IS NOT NULL
                AND execution_success_rate IS NOT NULL
                AND execution_success_rate_threshold IS NOT NULL
                AND execution_passed IS NOT NULL
                AND assertion_passed = (assertion_pass_rate >= assertion_pass_rate_threshold)
                AND execution_passed = (execution_success_rate >= execution_success_rate_threshold)
                AND (gate_status = 'PASS') = (assertion_passed AND execution_passed)
            )
        )
);

CREATE INDEX idx_quality_gate_status_run
    ON quality_gate_result(gate_status, test_run_id);

CREATE TABLE test_run_idempotency (
    idempotency_key     VARCHAR(100) PRIMARY KEY,
    request_fingerprint CHAR(64) NOT NULL,
    test_run_id         BIGINT NOT NULL UNIQUE,
    created_at          TIMESTAMPTZ(6) NOT NULL,
    expires_at          TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_test_run_idempotency_test_run
        FOREIGN KEY (test_run_id) REFERENCES test_run(id) ON DELETE RESTRICT,
    CONSTRAINT ck_test_run_idempotency_expiry
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_test_run_idempotency_expires_at
    ON test_run_idempotency(expires_at);

CREATE TABLE test_run_resolution_claim (
    test_run_id   BIGINT PRIMARY KEY,
    claim_token   UUID NOT NULL,
    lease_until   TIMESTAMPTZ(6) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    claimed_at    TIMESTAMPTZ(6) NOT NULL,
    updated_at    TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_test_run_resolution_claim_test_run
        FOREIGN KEY (test_run_id) REFERENCES test_run(id) ON DELETE RESTRICT,
    CONSTRAINT ck_test_run_resolution_claim_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_test_run_resolution_claim_lease
        CHECK (lease_until >= claimed_at)
);

CREATE TABLE test_execution_claim (
    snapshot_id   BIGINT PRIMARY KEY,
    claim_token   UUID NOT NULL,
    lease_until   TIMESTAMPTZ(6) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    claimed_at    TIMESTAMPTZ(6) NOT NULL,
    updated_at    TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_test_execution_claim_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES test_case_snapshot(id) ON DELETE RESTRICT,
    CONSTRAINT ck_test_execution_claim_attempt_count
        CHECK (attempt_count >= 0),
    CONSTRAINT ck_test_execution_claim_lease
        CHECK (lease_until >= claimed_at)
);

CREATE TABLE outbox_event (
    event_id          UUID PRIMARY KEY,
    event_type        VARCHAR(32) NOT NULL,
    schema_version    SMALLINT NOT NULL,
    payload           JSONB NOT NULL,
    deduplication_key TEXT NOT NULL UNIQUE,
    status            VARCHAR(16) NOT NULL,
    created_at        TIMESTAMPTZ(6) NOT NULL,
    published_at      TIMESTAMPTZ(6),

    CONSTRAINT ck_outbox_event_type
        CHECK (event_type IN (
            'TestRunRequested',
            'TestExecutionRequested',
            'TestExecutionCompleted'
        )),
    CONSTRAINT ck_outbox_event_schema_version
        CHECK (schema_version IN (1, 2)),
    CONSTRAINT ck_outbox_event_status
        CHECK (status IN ('PENDING', 'PUBLISHED')),
    CONSTRAINT ck_outbox_event_published_at
        CHECK (
            (status = 'PENDING' AND published_at IS NULL)
            OR (status = 'PUBLISHED' AND published_at IS NOT NULL)
        )
);

CREATE INDEX idx_outbox_event_status_created_at
    ON outbox_event(status, created_at);
