-- #173: Guardrail Evaluator/Profile을 Response Behavior Classifier로 교체.
-- MVP 초기화 전제이므로 legacy Guardrail evaluator catalog 데이터 보존을 위한 호환 계층은 만들지 않는다.

ALTER TABLE test_run
    DROP CONSTRAINT ck_test_run_evaluation_profile_pair,
    DROP COLUMN evaluation_checks,
    DROP COLUMN evaluation_strictness;

DROP TABLE bedrock_guardrail_evaluator;

ALTER TABLE evaluator_reference
    DROP CONSTRAINT ck_evaluator_reference_type,
    DROP COLUMN evaluator_type,
    ADD COLUMN provider_code VARCHAR(32),
    ADD COLUMN model_id TEXT;

ALTER TABLE evaluator_reference
    ALTER COLUMN provider_code SET NOT NULL,
    ALTER COLUMN model_id SET NOT NULL,
    ADD CONSTRAINT ck_evaluator_reference_provider_code_nonblank CHECK (provider_code ~ '[^[:space:]]'),
    ADD CONSTRAINT ck_evaluator_reference_model_id_nonblank CHECK (model_id ~ '[^[:space:]]');
