ALTER TABLE test_execution
    DROP CONSTRAINT ck_execution_result_shape;

ALTER TABLE test_execution
    DROP CONSTRAINT ck_execution_actual_action;

ALTER TABLE test_execution
    DROP COLUMN actual_action;

ALTER TABLE test_execution
    ADD CONSTRAINT ck_execution_result_shape
        CHECK (
            (
                result_status = 'SUCCEEDED'
                AND application_response IS NOT NULL
                AND evaluator_verdict IS NOT NULL
                AND error_code IS NULL
                AND error_message IS NULL
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL
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
                AND application_response IS NULL
                AND evaluator_verdict IS NULL
                AND error_code IS NULL
                AND error_message IS NULL
                AND error_stage IS NULL
                AND started_at IS NULL
                AND completed_at IS NULL
            )
        );
