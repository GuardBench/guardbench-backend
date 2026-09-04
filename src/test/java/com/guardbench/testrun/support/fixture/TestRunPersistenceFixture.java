package com.guardbench.testrun.support.fixture;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;

public final class TestRunPersistenceFixture {
    private final JdbcTemplate jdbcTemplate;

    public TestRunPersistenceFixture(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void clearPersistenceTables() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE outbox_event, test_suite, target_reference, evaluator_reference CASCADE");
    }

    public void insertTestSuite(long testSuiteId, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO test_suite(id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
                testSuiteId,
                "suite",
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    public void insertTestCase(long testCaseId, long testSuiteId, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO test_case(id, test_suite_id, name, input, expected_action, severity, category, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                testCaseId,
                testSuiteId,
                "case",
                "input",
                "ALLOW",
                "HIGH",
                "category",
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    public void insertQueuedTestRun(long testRunId, long testSuiteId, int testCaseCount, Instant createdAt) {
        String targetReference = "target-ref-" + testRunId;
        String evaluatorReference = "evaluator-ref-" + testRunId;
        insertTargetReference(targetReference);
        insertEvaluatorReference(evaluatorReference);
        jdbcTemplate.update(
                """
                INSERT INTO test_run(id, test_suite_id, status, test_case_count, processed_test_case_count,
                    target_reference_id, evaluator_reference_id, created_at, updated_at)
                VALUES (?, ?, 'QUEUED', ?, 0, ?, ?, ?, ?)
                """,
                testRunId,
                testSuiteId,
                testCaseCount,
                targetReference,
                evaluatorReference,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    public void insertTargetReference(String referenceId) {
        jdbcTemplate.update(
                "INSERT INTO target_reference(reference_id, target_type) VALUES (?, 'HTTP_ENDPOINT')",
                referenceId);
        jdbcTemplate.update(
                """
                INSERT INTO http_endpoint_target(
                    reference_id, endpoint_url, requested_revision, model
                ) VALUES (?, 'https://example.com/v1/chat/completions', 'v1', 'test-model')
                """,
                referenceId);
    }

    public void insertEvaluatorReference(String referenceId) {
        jdbcTemplate.update(
                "INSERT INTO evaluator_reference(reference_id, provider_code, model_id) VALUES (?, 'BEDROCK', 'test-classifier-model')",
                referenceId);
    }

    public void insertSnapshot(long snapshotId, long testRunId, long sourceTestCaseId, Instant createdAt) {
        insertSnapshot(snapshotId, testRunId, sourceTestCaseId, "input", createdAt);
    }

    public void insertSnapshot(long snapshotId, long testRunId, long sourceTestCaseId, String input, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO test_case_snapshot(
                    id, test_run_id, source_test_case_id, name, input,
                    expected_action, severity, category, created_at
                ) VALUES (?, ?, ?, 'case', ?, 'ALLOW', 'HIGH', 'category', ?)
                """,
                snapshotId,
                testRunId,
                sourceTestCaseId,
                input,
                Timestamp.from(createdAt)
        );
    }
}
