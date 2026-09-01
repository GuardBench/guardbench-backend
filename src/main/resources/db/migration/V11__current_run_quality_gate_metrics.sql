ALTER TABLE quality_gate_result
    DROP CONSTRAINT ck_quality_gate_metric_ranges,
    DROP CONSTRAINT ck_quality_gate_result_shape;

ALTER TABLE quality_gate_result
    RENAME COLUMN candidate_assertion_pass_rate TO assertion_pass_rate;

ALTER TABLE quality_gate_result
    RENAME COLUMN test_execution_success_rate TO execution_success_rate;

ALTER TABLE quality_gate_result
    DROP COLUMN security_regression_count,
    DROP COLUMN security_regression_rate,
    DROP COLUMN usability_regression_rate;

ALTER TABLE quality_gate_result
    ADD CONSTRAINT ck_quality_gate_metric_ranges
        CHECK (
            (assertion_pass_rate IS NULL OR assertion_pass_rate BETWEEN 0.0 AND 1.0)
            AND (execution_success_rate IS NULL OR execution_success_rate BETWEEN 0.0 AND 1.0)
        ),
    ADD CONSTRAINT ck_quality_gate_result_shape
        CHECK (
            (
                gate_status = 'NOT_EVALUATED'
                AND assertion_pass_rate IS NULL
                AND execution_success_rate IS NULL
            )
            OR (
                gate_status IN ('PASS', 'FAIL')
                AND assertion_pass_rate IS NOT NULL
                AND execution_success_rate IS NOT NULL
            )
        );
