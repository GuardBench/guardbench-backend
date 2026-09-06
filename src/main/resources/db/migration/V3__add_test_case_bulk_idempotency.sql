CREATE TABLE test_case_bulk_idempotency (
    idempotency_key       VARCHAR(100) PRIMARY KEY,
    request_fingerprint   CHAR(64) NOT NULL,
    test_suite_id         BIGINT NOT NULL,
    created_test_case_ids BIGINT[],
    total_test_case_count BIGINT,
    created_at            TIMESTAMPTZ(6) NOT NULL,
    expires_at            TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT fk_test_case_bulk_idempotency_suite
        FOREIGN KEY (test_suite_id) REFERENCES test_suite(id) ON DELETE CASCADE,
    CONSTRAINT ck_test_case_bulk_idempotency_expiry
        CHECK (expires_at > created_at),
    CONSTRAINT ck_test_case_bulk_idempotency_completion
        CHECK (
            (created_test_case_ids IS NULL AND total_test_case_count IS NULL)
            OR (
                cardinality(created_test_case_ids) BETWEEN 1 AND 1000
                AND total_test_case_count >= cardinality(created_test_case_ids)
            )
        )
);

CREATE INDEX idx_test_case_bulk_idempotency_expires_at
    ON test_case_bulk_idempotency(expires_at);
