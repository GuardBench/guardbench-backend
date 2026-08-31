CREATE TABLE evaluator_reference (
    reference_id TEXT PRIMARY KEY,
    evaluator_type VARCHAR(32) NOT NULL,
    CONSTRAINT ck_evaluator_reference_type CHECK (evaluator_type = 'BEDROCK_GUARDRAIL')
);

CREATE TABLE bedrock_guardrail_evaluator (
    reference_id TEXT PRIMARY KEY,
    guardrail_identifier TEXT NOT NULL,
    guardrail_revision VARCHAR(8) NOT NULL,
    CONSTRAINT fk_bedrock_guardrail_evaluator_reference FOREIGN KEY (reference_id)
        REFERENCES evaluator_reference(reference_id) ON DELETE RESTRICT,
    CONSTRAINT ck_bedrock_guardrail_evaluator_identifier_nonblank CHECK (guardrail_identifier ~ '[^[:space:]]'),
    CONSTRAINT ck_bedrock_guardrail_evaluator_revision CHECK (guardrail_revision ~ '^[1-9][0-9]{0,7}$')
);

ALTER TABLE test_run
    ADD COLUMN evaluation_checks TEXT,
    ADD COLUMN evaluation_strictness VARCHAR(16),
    ADD COLUMN evaluator_reference_id TEXT,
    ADD CONSTRAINT fk_test_run_evaluator_reference FOREIGN KEY (evaluator_reference_id)
        REFERENCES evaluator_reference(reference_id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_test_run_evaluation_profile_pair CHECK (
        (evaluation_checks IS NULL AND evaluation_strictness IS NULL AND evaluator_reference_id IS NULL)
        OR (evaluation_checks ~ '^[A-Z_]+(,[A-Z_]+)*$'
            AND evaluation_strictness IN ('RELAXED', 'STANDARD', 'STRICT')
            AND evaluator_reference_id IS NOT NULL)
    );

ALTER TABLE http_endpoint_target
    ADD COLUMN requested_revision TEXT,
    ADD CONSTRAINT ck_http_endpoint_target_revision_nonblank
        CHECK (requested_revision IS NULL OR requested_revision ~ '[^[:space:]]');
