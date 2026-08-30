CREATE TABLE target_reference (
    reference_id TEXT PRIMARY KEY,
    target_type  VARCHAR(32) NOT NULL,

    CONSTRAINT ck_target_reference_id_nonblank
        CHECK (reference_id ~ '[^[:space:]]'),
    CONSTRAINT ck_target_reference_type
        CHECK (target_type IN ('BEDROCK_GUARDRAIL'))
);

CREATE TABLE bedrock_guardrail_target (
    reference_id        TEXT PRIMARY KEY,
    guardrail_identifier TEXT NOT NULL,
    requested_revision   TEXT NOT NULL,
    resolved_revision    TEXT,

    CONSTRAINT fk_bedrock_guardrail_target_reference
        FOREIGN KEY (reference_id) REFERENCES target_reference(reference_id) ON DELETE RESTRICT,
    CONSTRAINT ck_bedrock_guardrail_identifier_nonblank
        CHECK (guardrail_identifier ~ '[^[:space:]]'),
    CONSTRAINT ck_bedrock_guardrail_requested_revision
        CHECK (requested_revision = 'DRAFT' OR requested_revision ~ '^[1-9][0-9]{0,7}$'),
    CONSTRAINT ck_bedrock_guardrail_resolved_revision
        CHECK (resolved_revision IS NULL OR resolved_revision ~ '^[1-9][0-9]{0,7}$'),
    CONSTRAINT ck_bedrock_guardrail_revision_shape
        CHECK (
            requested_revision = 'DRAFT'
            OR resolved_revision = requested_revision
        )
);

INSERT INTO target_reference(reference_id, target_type)
SELECT 'legacy-test-run-' || id, 'BEDROCK_GUARDRAIL'
FROM test_run;

INSERT INTO bedrock_guardrail_target(
    reference_id,
    guardrail_identifier,
    requested_revision,
    resolved_revision
)
SELECT 'legacy-test-run-' || id,
       candidate_guardrail_id,
       candidate_requested_source,
       candidate_resolved_version
FROM test_run;

ALTER TABLE test_run
    ADD COLUMN target_reference_id TEXT;

UPDATE test_run
SET target_reference_id = 'legacy-test-run-' || id;

ALTER TABLE test_run
    ALTER COLUMN target_reference_id SET NOT NULL,
    ADD CONSTRAINT fk_test_run_target_reference
        FOREIGN KEY (target_reference_id) REFERENCES target_reference(reference_id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_test_run_target_reference UNIQUE (target_reference_id),
    DROP CONSTRAINT ck_test_run_guardrail_ids,
    DROP CONSTRAINT ck_test_run_versions,
    DROP CONSTRAINT ck_test_run_candidate_source,
    DROP CONSTRAINT ck_test_run_lifecycle,
    DROP COLUMN baseline_guardrail_id,
    DROP COLUMN baseline_version,
    DROP COLUMN candidate_guardrail_id,
    DROP COLUMN candidate_requested_source,
    DROP COLUMN candidate_resolved_version;

ALTER TABLE test_run
    ADD CONSTRAINT ck_test_run_lifecycle
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
        );

DELETE FROM change_result;

UPDATE quality_gate_result
SET gate_status = 'NOT_EVALUATED',
    candidate_assertion_pass_rate = NULL,
    security_regression_count = NULL,
    security_regression_rate = NULL,
    usability_regression_rate = NULL,
    test_execution_success_rate = NULL;

DELETE FROM test_execution
WHERE target_type = 'BASELINE';

ALTER TABLE test_execution
    DROP CONSTRAINT pk_test_execution,
    DROP CONSTRAINT ck_execution_target_type,
    DROP COLUMN target_type,
    ADD CONSTRAINT pk_test_execution PRIMARY KEY (snapshot_id);

DELETE FROM test_execution_claim
WHERE target_type = 'BASELINE';

ALTER TABLE test_execution_claim
    DROP CONSTRAINT pk_test_execution_claim,
    DROP CONSTRAINT ck_test_execution_claim_target_type,
    DROP COLUMN target_type,
    ADD CONSTRAINT pk_test_execution_claim PRIMARY KEY (snapshot_id);

ALTER TABLE outbox_event
    DROP CONSTRAINT ck_outbox_event_schema_version;

DELETE FROM outbox_event
WHERE status = 'PENDING'
  AND event_type IN ('TestExecutionRequested', 'TestExecutionCompleted')
  AND payload ->> 'targetType' = 'BASELINE';

UPDATE outbox_event
SET schema_version = 2,
    payload = jsonb_set(payload - 'targetType', '{schemaVersion}', '2'::jsonb),
    deduplication_key = regexp_replace(deduplication_key, ':CANDIDATE$', '')
WHERE status = 'PENDING'
  AND event_type IN ('TestExecutionRequested', 'TestExecutionCompleted')
  AND payload ->> 'targetType' = 'CANDIDATE';

UPDATE outbox_event
SET schema_version = 2,
    payload = jsonb_set(payload, '{schemaVersion}', '2'::jsonb)
WHERE status = 'PENDING'
  AND event_type = 'TestRunRequested';

ALTER TABLE outbox_event
    ADD CONSTRAINT ck_outbox_event_schema_version
        CHECK (schema_version IN (1, 2));
