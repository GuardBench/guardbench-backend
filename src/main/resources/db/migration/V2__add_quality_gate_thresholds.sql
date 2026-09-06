ALTER TABLE test_run
    ADD COLUMN assertion_pass_rate_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.95,
    ADD COLUMN execution_success_rate_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.95;

ALTER TABLE test_run
    ADD CONSTRAINT ck_test_run_quality_gate_thresholds
        CHECK (
            assertion_pass_rate_threshold BETWEEN 0.0 AND 1.0
            AND execution_success_rate_threshold BETWEEN 0.0 AND 1.0
        );
