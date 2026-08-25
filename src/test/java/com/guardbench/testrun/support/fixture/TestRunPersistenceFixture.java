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
        jdbcTemplate.execute("TRUNCATE TABLE outbox_event, test_suite CASCADE");
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
        jdbcTemplate.update(
                """
                INSERT INTO test_run(id, test_suite_id, status, test_case_count, processed_test_case_count,
                    baseline_guardrail_id, baseline_version, candidate_guardrail_id,
                    candidate_requested_source, created_at, updated_at)
                VALUES (?, ?, 'QUEUED', ?, 0, 'guardrail', '1', 'guardrail', 'DRAFT', ?, ?)
                """,
                testRunId,
                testSuiteId,
                testCaseCount,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    public void insertSnapshot(long snapshotId, long testRunId, long sourceTestCaseId, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO test_case_snapshot(
                    id, test_run_id, source_test_case_id, name, input,
                    expected_action, severity, category, created_at
                ) VALUES (?, ?, ?, 'case', 'input', 'ALLOW', 'HIGH', 'category', ?)
                """,
                snapshotId,
                testRunId,
                sourceTestCaseId,
                Timestamp.from(createdAt)
        );
    }
}
