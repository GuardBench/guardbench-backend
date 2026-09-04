package com.guardbench.testrun.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testsupport.PostgresTestConfiguration;
import com.guardbench.testrun.application.port.out.LoadTestRunRegressionPort;
import com.guardbench.testrun.application.port.out.PageCriteria;
import com.guardbench.testrun.application.port.out.TestRunRegressionSnapshot;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class TestRunRegressionPersistenceAdapterIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-08-25T10:00:00Z");

    @Autowired
    private LoadTestRunRegressionPort port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void listsFinishedRunsWithSameSnapshotDefinitionAndEvaluatorConfiguration() {
        insertSuite(70_001L);
        insertRun(80_001L, 70_001L, "evaluator-current", "same", "2026-08-27T10:00:00Z");
        insertRun(80_002L, 70_001L, "evaluator-history", "same", "2026-08-26T10:00:00Z");
        insertRun(80_003L, 70_001L, "evaluator-other", "other", "2026-08-25T10:00:00Z");
        insertRun(80_000L, 70_001L, "evaluator-tied", "same", "2026-08-27T10:00:00Z");
        insertRun(80_004L, 70_001L, "evaluator-future", "same", "2026-08-28T10:00:00Z");
        insertSnapshot(90_001L, 80_001L, "same case", "input", "BLOCK");
        insertSnapshot(90_002L, 80_002L, "same case", "input", "BLOCK");
        insertSnapshot(90_003L, 80_003L, "different case", "input", "BLOCK");
        insertSnapshot(90_000L, 80_000L, "same case", "input", "BLOCK");
        insertSnapshot(90_004L, 80_004L, "same case", "input", "BLOCK");

        var result = port.loadComparableRuns(80_001L, new PageCriteria(1, 20));

        assertEquals(List.of(80_000L, 80_002L), result.items().stream().map(item -> item.id()).toList());
        assertEquals(2L, result.totalElements());
    }

    @Test
    void loadsStoredVerdictWithoutCallingAnyExternalAdapter() {
        insertSuite(70_011L);
        insertRun(80_011L, 70_011L, "evaluator-current-2", "same", "2026-08-27T10:00:00Z");
        insertSnapshot(90_011L, 80_011L, "case", "input", "BLOCK");
        insertExecution(90_011L, "ALLOW");

        TestRunRegressionSnapshot snapshot = port.loadSnapshots(80_011L).getFirst();

        assertEquals(90_011L, snapshot.snapshotId());
        assertEquals("BLOCK", snapshot.expectedAction().name());
        assertEquals("ALLOW", snapshot.evaluatorVerdict().name());
        assertNull(port.loadSnapshots(99_999L).stream().findFirst().orElse(null));
    }

    private void insertSuite(long id) {
        jdbcTemplate.update("""
                INSERT INTO test_suite (id, name, description, created_at, updated_at)
                VALUES (?, 'Suite', NULL, ?, ?)
                """, id, Timestamp.from(T0), Timestamp.from(T0));
        jdbcTemplate.update("""
                INSERT INTO test_case (
                    id, test_suite_id, name, input, expected_action, severity, category,
                    created_at, updated_at)
                VALUES (11, ?, 'case', 'input', 'BLOCK', 'HIGH', 'PII', ?, ?)
                """, id, Timestamp.from(T0), Timestamp.from(T0));
    }

    private void insertRun(long id, long suiteId, String evaluatorId, String modelId, String completedAt) {
        String targetId = "target-" + id;
        jdbcTemplate.update("INSERT INTO target_reference(reference_id, target_type) VALUES (?, 'HTTP_ENDPOINT')", targetId);
        jdbcTemplate.update("INSERT INTO http_endpoint_target(reference_id, endpoint_url, model) VALUES (?, ?, 'model')",
                targetId, "https://example.com/" + id);
        jdbcTemplate.update("INSERT INTO evaluator_reference(reference_id, provider_code, model_id) VALUES (?, 'BEDROCK', ?)",
                evaluatorId, modelId);
        jdbcTemplate.update("""
                INSERT INTO test_run (
                    id, test_suite_id, status, test_case_count, processed_test_case_count,
                    target_reference_id, evaluator_reference_id,
                    execution_outcome, created_at, started_at, completed_at, updated_at)
                VALUES (?, ?, 'FINISHED', 1, 1, ?, ?, 'COMPLETED', ?, ?, ?, ?)
                """, id, suiteId, targetId, evaluatorId, Timestamp.from(T0), Timestamp.from(T0),
                Timestamp.from(Instant.parse(completedAt)), Timestamp.from(Instant.parse(completedAt)));
    }

    private void insertSnapshot(long id, long runId, String name, String input, String expectedAction) {
        jdbcTemplate.update("""
                INSERT INTO test_case_snapshot (
                    id, test_run_id, source_test_case_id, name, input, expected_action, severity, category, created_at)
                VALUES (?, ?, 11, ?, ?, ?, 'HIGH', 'PII', ?)
                """, id, runId, name, input, expectedAction, Timestamp.from(T0));
    }

    private void insertExecution(long snapshotId, String verdict) {
        jdbcTemplate.update("""
                INSERT INTO test_execution (
                    snapshot_id, result_status, application_response, evaluator_verdict,
                    started_at, completed_at)
                VALUES (?, 'SUCCEEDED', ?, ?, ?, ?)
                """, snapshotId, "response", verdict, Timestamp.from(T0), Timestamp.from(T0));
    }
}
