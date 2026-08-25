CREATE TABLE test_run_idempotency (
    idempotency_key    VARCHAR(100) PRIMARY KEY,
    request_fingerprint CHAR(64) NOT NULL,
    test_run_id        BIGINT NOT NULL UNIQUE,
    created_at         TIMESTAMPTZ(6) NOT NULL,
    expires_at         TIMESTAMPTZ(6) NOT NULL,

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
    snapshot_id   BIGINT NOT NULL,
    target_type   VARCHAR(16) NOT NULL,
    claim_token   UUID NOT NULL,
    lease_until   TIMESTAMPTZ(6) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    claimed_at    TIMESTAMPTZ(6) NOT NULL,
    updated_at    TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT pk_test_execution_claim PRIMARY KEY (snapshot_id, target_type),
    CONSTRAINT fk_test_execution_claim_snapshot
        FOREIGN KEY (snapshot_id) REFERENCES test_case_snapshot(id) ON DELETE RESTRICT,
    CONSTRAINT ck_test_execution_claim_target_type
        CHECK (target_type IN ('BASELINE', 'CANDIDATE')),
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
        CHECK (schema_version = 1),
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
