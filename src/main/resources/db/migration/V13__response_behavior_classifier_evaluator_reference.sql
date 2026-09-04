-- #173: Guardrail Evaluator/Profile을 Response Behavior Classifier로 교체.
-- MVP 초기화 전제이므로 legacy Guardrail evaluator catalog 데이터 보존을 위한 호환 계층은 만들지 않는다.
-- 기존 evaluator_reference/test_run 행이 있으면 새 NOT NULL 컬럼을 즉시 추가할 수 없으므로,
-- 컬럼 교체 전 TestRun 계열 데이터를 완전히 정리한다(V3의 legacy 데이터 정리 선례와 동일한 접근).

DELETE FROM quality_gate_result;
DELETE FROM change_result;
DELETE FROM assertion_result;
DELETE FROM test_execution;
DELETE FROM test_case_snapshot;
DELETE FROM outbox_event;

ALTER TABLE test_run
    DROP CONSTRAINT ck_test_run_evaluation_profile_pair,
    DROP COLUMN evaluation_checks,
    DROP COLUMN evaluation_strictness;

DELETE FROM test_run;

DROP TABLE bedrock_guardrail_evaluator;

DELETE FROM evaluator_reference;

ALTER TABLE evaluator_reference
    DROP CONSTRAINT ck_evaluator_reference_type,
    DROP COLUMN evaluator_type,
    ADD COLUMN provider_code VARCHAR(32) NOT NULL,
    ADD COLUMN model_id TEXT NOT NULL,
    ADD CONSTRAINT ck_evaluator_reference_provider_code_nonblank CHECK (provider_code ~ '[^[:space:]]'),
    ADD CONSTRAINT ck_evaluator_reference_model_id_nonblank CHECK (model_id ~ '[^[:space:]]');
