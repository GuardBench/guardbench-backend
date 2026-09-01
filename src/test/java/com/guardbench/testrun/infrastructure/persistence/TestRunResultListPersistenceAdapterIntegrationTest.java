package com.guardbench.testrun.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testrun.application.port.out.LoadTestRunResultListPort;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.SortOrder;
import com.guardbench.testrun.application.port.out.TestRunResultItem;
import com.guardbench.testrun.application.port.out.TestRunResultListCriteria;
import com.guardbench.testrun.application.port.out.TestRunResultSortField;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * TestRun 개별 결과 목록의 Baseline/Candidate 실행, Assertion/Change 결과 조합과 filter·정렬을 실제
 * PostgreSQL에서 검증한다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1</a>
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class TestRunResultListPersistenceAdapterIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-08-25T10:00:00Z");

    @Autowired
    private LoadTestRunResultListPort port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Assertion과 Change 결과가 없으면 관련 code는 모두 null이다")
    void returnsNullEvaluationCodesWhenNoAssertionOrChangeResult() {
        insertSuite(70_001L);
        insertTestRun(80_001L, 70_001L);
        insertSnapshot(90_001L, 80_001L, 1L, "case", "input", "ALLOW", "LOW", "PII");
        insertExecution(90_001L, "FAILED", null, "PROVIDER_ERROR", "안전한 오류");

        PageResult<TestRunResultItem> result = port.load(
                80_001L, TestRunResultListCriteria.firstPage());

        TestRunResultItem item = result.items().getFirst();
        assertNull(item.assertionStatusCode());
        assertEquals("PROVIDER_ERROR", item.execution().errorCode());
    }

    @Test
    @DisplayName("changeType filter는 comparabilityStatus가 NOT_COMPARABLE인 결과를 제외한다")
    void excludesNotComparableResultsWhenFilteringByChangeType() {
        insertSuite(70_011L);
        insertTestRun(80_011L, 70_011L);
        insertSnapshot(90_011L, 80_011L, 1L, "comparable", "input", "BLOCK", "HIGH", "PII");
        insertExecution(90_011L, "SUCCEEDED", "ALLOW", null, null);
        insertAssertion(90_011L, "FAIL");

        insertSnapshot(90_012L, 80_011L, 2L, "not comparable", "input", "BLOCK", "HIGH", "PII");
        insertExecution(90_012L, "FAILED", null, "PROVIDER_ERROR", "오류");

        PageResult<TestRunResultItem> result = port.load(80_011L, new TestRunResultListCriteria(
                null, null, null, null, null, null, "FAIL",
                List.of(), com.guardbench.testrun.application.port.out.PageCriteria.firstPage()));

        assertEquals(List.of(90_011L), result.items().stream().map(TestRunResultItem::snapshotId).toList());
    }

    @Test
    @DisplayName("Severity 정렬은 저장 code 사전순이 아니라 LOW, MEDIUM, HIGH, CRITICAL 순서를 따른다")
    void sortsBySeverityMeaningOrderNotAlphabetically() {
        insertSuite(70_021L);
        insertTestRun(80_021L, 70_021L);
        insertSnapshot(90_021L, 80_021L, 1L, "critical", "input", "BLOCK", "CRITICAL", "PII");
        insertExecution(90_021L, "SUCCEEDED", "BLOCK", null, null);
        insertSnapshot(90_022L, 80_021L, 2L, "low", "input", "BLOCK", "LOW", "PII");
        insertExecution(90_022L, "SUCCEEDED", "BLOCK", null, null);

        PageResult<TestRunResultItem> result = port.load(80_021L, new TestRunResultListCriteria(
                null, null, null, null, null, null, null,
                List.of(SortOrder.asc(TestRunResultSortField.SEVERITY)),
                com.guardbench.testrun.application.port.out.PageCriteria.firstPage()));

        assertEquals(List.of(90_022L, 90_021L), result.items().stream().map(TestRunResultItem::snapshotId).toList());
    }

    @Test
    @DisplayName("Evaluator verdict와 ExpectedResult로 evaluationOutcome을 계산하고 필터링한다")
    void calculatesAndFiltersEvaluationOutcome() {
        insertSuite(70_031L);
        insertTestRun(80_031L, 70_031L);
        insertSnapshot(90_031L, 80_031L, 1L, "blocked", "input", "BLOCK", "LOW", "PII");
        insertModernExecution(90_031L, "BLOCK", "blocked response");
        insertSnapshot(90_032L, 80_031L, 2L, "allowed", "input", "BLOCK", "LOW", "PII");
        insertModernExecution(90_032L, "ALLOW", "allowed response");

        PageResult<TestRunResultItem> result = port.load(80_031L, new TestRunResultListCriteria(
                null, null, null, null, null, null, null, "TRUE_POSITIVE",
                List.of(), com.guardbench.testrun.application.port.out.PageCriteria.firstPage()));

        assertEquals(List.of(90_031L), result.items().stream().map(TestRunResultItem::snapshotId).toList());
        TestRunResultItem item = result.items().getFirst();
        assertEquals("BLOCK", item.execution().evaluatorVerdict().name());
        assertEquals("TRUE_POSITIVE", item.evaluationOutcomeCode());
    }

    private void insertSuite(long id) {
        jdbcTemplate.update("""
                INSERT INTO test_suite (id, name, description, created_at, updated_at)
                VALUES (?, 'Suite', NULL, ?, ?)
                """, id, Timestamp.from(T0), Timestamp.from(T0));
    }

    private void insertTestRun(long id, long suiteId) {
        String targetReference = "target-ref-" + id;
        jdbcTemplate.update("INSERT INTO target_reference(reference_id, target_type) VALUES (?, 'BEDROCK_GUARDRAIL')",
                targetReference);
        jdbcTemplate.update("""
                INSERT INTO test_run (
                    id, test_suite_id, status, test_case_count, processed_test_case_count,
                    target_reference_id, execution_outcome,
                    created_at, started_at, completed_at, updated_at)
                VALUES (?, ?, 'FINISHED', 1, 1, ?, 'COMPLETED', ?, ?, ?, ?)
                """, id, suiteId, targetReference,
                Timestamp.from(T0), Timestamp.from(T0), Timestamp.from(T0), Timestamp.from(T0));
    }

    private void insertTestCase(long id, long suiteId) {
        jdbcTemplate.update("""
                INSERT INTO test_case (
                    id, test_suite_id, name, input, expected_action, severity, category,
                    created_at, updated_at, deleted_at)
                VALUES (?, ?, 'case', 'input', 'BLOCK', 'LOW', 'PII', ?, ?, NULL)
                """, id, suiteId, Timestamp.from(T0), Timestamp.from(T0));
    }

    private void insertSnapshot(
            long id, long testRunId, long sourceTestCaseId, String name, String input,
            String expectedAction, String severity, String category) {
        insertTestCase(sourceTestCaseId, jdbcTemplate.queryForObject(
                "SELECT test_suite_id FROM test_run WHERE id = ?", Long.class, testRunId));
        jdbcTemplate.update("""
                INSERT INTO test_case_snapshot (
                    id, test_run_id, source_test_case_id, name, input, expected_action, severity,
                    category, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, testRunId, sourceTestCaseId, name, input, expectedAction, severity, category,
                Timestamp.from(T0));
    }

    private void insertExecution(
            long snapshotId, String status, String actualAction,
            String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                INSERT INTO test_execution (
                    snapshot_id, result_status, actual_action, error_code,
                    error_message, started_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, snapshotId, status, actualAction, errorCode, errorMessage,
                Timestamp.from(T0), Timestamp.from(T0));
    }

    private void insertModernExecution(long snapshotId, String evaluatorVerdict, String applicationResponse) {
        jdbcTemplate.update("""
                INSERT INTO test_execution (
                    snapshot_id, result_status, application_response, evaluator_verdict,
                    started_at, completed_at)
                VALUES (?, 'SUCCEEDED', ?, ?, ?, ?)
                """, snapshotId, applicationResponse, evaluatorVerdict,
                Timestamp.from(T0), Timestamp.from(T0));
    }

    private void insertAssertion(long snapshotId, String status) {
        jdbcTemplate.update("""
                INSERT INTO assertion_result (snapshot_id, assertion_status, created_at)
                VALUES (?, ?, ?)
                """, snapshotId, status, Timestamp.from(T0));
    }

}
