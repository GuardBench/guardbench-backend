ALTER TABLE test_execution
    ADD COLUMN application_response TEXT,
    ADD COLUMN evaluator_verdict VARCHAR(16),
    ADD COLUMN error_stage VARCHAR(32);

ALTER TABLE test_execution
    DROP CONSTRAINT ck_execution_result_shape;

ALTER TABLE test_execution
    ADD CONSTRAINT ck_execution_application_response
        CHECK (application_response IS NULL OR application_response ~ '[^[:space:]]'),
    ADD CONSTRAINT ck_execution_evaluator_verdict
        CHECK (evaluator_verdict IS NULL OR evaluator_verdict IN ('ALLOW', 'BLOCK')),
    ADD CONSTRAINT ck_execution_error_stage
        CHECK (error_stage IS NULL OR error_stage IN ('APPLICATION_TARGET', 'EVALUATOR')),
    ADD CONSTRAINT ck_execution_result_shape
        CHECK (
            (
                result_status = 'SUCCEEDED'
                AND COALESCE(evaluator_verdict, actual_action) IS NOT NULL
                AND error_code IS NULL
                AND error_message IS NULL
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL
                AND (application_response IS NOT NULL OR actual_action IS NOT NULL)
            )
            OR (
                result_status IN ('FAILED', 'TIMED_OUT')
                AND evaluator_verdict IS NULL
                AND error_code IS NOT NULL
                AND error_message IS NOT NULL
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL
            )
            OR (
                result_status = 'NOT_STARTED'
                AND actual_action IS NULL
                AND application_response IS NULL
                AND evaluator_verdict IS NULL
                AND error_code IS NULL
                AND error_message IS NULL
                AND error_stage IS NULL
                AND started_at IS NULL
                AND completed_at IS NULL
            )
        );
