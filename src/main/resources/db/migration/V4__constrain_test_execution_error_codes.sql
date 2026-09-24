ALTER TABLE test_execution
    ADD CONSTRAINT ck_execution_error_code
        CHECK (
            error_code IS NULL OR error_code IN (
                'TARGET_NOT_FOUND',
                'TARGET_ACCESS_DENIED',
                'TARGET_CONFIGURATION_INVALID',
                'EVALUATOR_NOT_FOUND',
                'EVALUATOR_ACCESS_DENIED',
                'EVALUATOR_CONFIGURATION_INVALID',
                'PROVIDER_UNAVAILABLE',
                'PROVIDER_RESPONSE_INVALID',
                'PROVIDER_TIMEOUT'
            )
        ),
    ADD CONSTRAINT ck_execution_timeout_error_code
        CHECK (result_status <> 'TIMED_OUT' OR error_code = 'PROVIDER_TIMEOUT');
