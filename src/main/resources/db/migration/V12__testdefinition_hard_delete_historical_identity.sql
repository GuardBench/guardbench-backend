ALTER TABLE test_case
    DROP CONSTRAINT ck_test_case_time_order;

DROP INDEX idx_test_case_active_suite_created;
DROP INDEX idx_test_case_active_suite_category;
DROP INDEX idx_test_case_active_suite_expected_action;
DROP INDEX idx_test_case_active_suite_severity;

ALTER TABLE test_case
    DROP COLUMN deleted_at;

ALTER TABLE test_case_snapshot
    DROP CONSTRAINT fk_snapshot_source_test_case;

ALTER TABLE test_run
    DROP CONSTRAINT fk_test_run_suite;
