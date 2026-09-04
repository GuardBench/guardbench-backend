package com.guardbench.testrun.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testrun.application.port.out.EvaluatorMetricsView;
import com.guardbench.testrun.application.port.out.LoadTestRunEvaluatorMetricsPort;
import com.guardbench.testrun.application.port.out.LoadTestRunResultListPort;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.SortOrder;
import com.guardbench.testrun.application.port.out.TestRunResultAttentionType;
import com.guardbench.testrun.application.port.out.TestRunResultItem;
import com.guardbench.testrun.application.port.out.TestRunResultListView;
import com.guardbench.testrun.application.port.out.TestRunResultListCriteria;
import com.guardbench.testrun.application.port.out.TestRunResultSortField;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * TestRun 개별 결과 목록의 단일 실행, Assertion 결과 조합과 filter·정렬을 실제 PostgreSQL에서
 * 검증한다.
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
    private LoadTestRunEvaluatorMetricsPort metricsPort;

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
                80_001L, TestRunResultListCriteria.firstPage()).page();

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
                null, null, null, null, null, null, "FAIL", null,
                Set.of(), false, List.of(),
                com.guardbench.testrun.application.port.out.PageCriteria.firstPage())).page();

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
                null, null, null, null, null, null, null, null,
                Set.of(), false,
                List.of(SortOrder.asc(TestRunResultSortField.SEVERITY)),
                com.guardbench.testrun.application.port.out.PageCriteria.firstPage())).page();

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
                Set.of(), false, List.of(),
                com.guardbench.testrun.application.port.out.PageCriteria.firstPage())).page();

        assertEquals(List.of(90_031L), result.items().stream().map(TestRunResultItem::snapshotId).toList());
        TestRunResultItem item = result.items().getFirst();
        assertEquals("BLOCK", item.execution().evaluatorVerdict().name());
        assertEquals("TRUE_POSITIVE", item.evaluationOutcomeCode());
    }

    @Test
    @DisplayName("attentionType은 같은 필드끼리 OR, 일반 필터와 AND로 결합하고 facets는 선택한 유형을 무시한다")
    void filtersAttentionTypesAndReturnsUnselectedFacetCounts() {
        insertAttentionFixtures(70_061L, 80_061L);

        TestRunResultListView result = port.load(80_061L, new TestRunResultListCriteria(
                null, null, "PII", null, null, null, null, null,
                Set.of(TestRunResultAttentionType.FALSE_NEGATIVE, TestRunResultAttentionType.TIMED_OUT),
                true, List.of(),
                com.guardbench.testrun.application.port.out.PageCriteria.firstPage()));

        assertEquals(List.of(90_061L, 90_063L),
                result.page().items().stream().map(TestRunResultItem::snapshotId).toList());
        assertEquals(List.of(
                        TestRunResultAttentionType.FALSE_NEGATIVE,
                        TestRunResultAttentionType.TIMED_OUT),
                result.page().items().stream().map(TestRunResultItem::attentionType).toList());
        assertEquals(2L, result.page().totalElements());
        assertEquals(6L, result.facets().allResults());
        assertEquals(5L, result.facets().attentionTotal());
        assertEquals(1L, result.facets().falseNegative());
        assertEquals(1L, result.facets().falsePositive());
        assertEquals(1L, result.facets().executionFailed());
        assertEquals(1L, result.facets().timedOut());
        assertEquals(1L, result.facets().notStarted());
    }

    @Test
    @DisplayName("Attention 기본 정렬은 위험도, 유형 우선순위, Snapshot ID 순으로 적용한 뒤 페이지를 자른다")
    void sortsAttentionBeforePagination() {
        insertAttentionFixtures(70_071L, 80_071L);

        TestRunResultListView result = port.load(80_071L, new TestRunResultListCriteria(
                null, null, "PII", null, null, null, null, null,
                Set.of(TestRunResultAttentionType.values()), false, List.of(),
                new com.guardbench.testrun.application.port.out.PageCriteria(2, 2)));

        assertEquals(List.of(90_064L, 90_063L),
                result.page().items().stream().map(TestRunResultItem::snapshotId).toList());
        assertEquals(5L, result.page().totalElements());
    }

    @Test
    @DisplayName("전체 TestRun 결과를 TP/TN/FP/FN으로 집계하고 verdict 없는 실행은 제외한다")
    void aggregatesAllStoredVerdictsAcrossTestRun() {
        insertSuite(70_041L);
        insertTestRun(80_041L, 70_041L);
        insertSnapshot(90_041L, 80_041L, 1L, "tp1", "input", "BLOCK", "LOW", "PII");
        insertModernExecution(90_041L, "BLOCK", "response");
        insertSnapshot(90_042L, 80_041L, 2L, "tp2", "input", "BLOCK", "LOW", "PII");
        insertModernExecution(90_042L, "BLOCK", "response");
        insertSnapshot(90_043L, 80_041L, 3L, "tn1", "input", "ALLOW", "LOW", "PII");
        insertModernExecution(90_043L, "ALLOW", "response");
        insertSnapshot(90_044L, 80_041L, 4L, "tn2", "input", "ALLOW", "LOW", "PII");
        insertModernExecution(90_044L, "ALLOW", "response");
        insertSnapshot(90_045L, 80_041L, 5L, "tn3", "input", "ALLOW", "LOW", "PII");
        insertModernExecution(90_045L, "ALLOW", "response");
        insertSnapshot(90_046L, 80_041L, 6L, "fp", "input", "ALLOW", "LOW", "PII");
        insertModernExecution(90_046L, "BLOCK", "response");
        insertSnapshot(90_047L, 80_041L, 7L, "fn", "input", "BLOCK", "LOW", "PII");
        insertModernExecution(90_047L, "ALLOW", "response");
        insertSnapshot(90_048L, 80_041L, 8L, "not evaluated", "input", "BLOCK", "LOW", "PII");
        insertExecution(90_048L, "FAILED", null, "PROVIDER_ERROR", "안전한 오류");

        EvaluatorMetricsView metrics = metricsPort.load(80_041L);

        assertEquals(2L, metrics.truePositive());
        assertEquals(3L, metrics.trueNegative());
        assertEquals(1L, metrics.falsePositive());
        assertEquals(1L, metrics.falseNegative());
        assertEquals(0.25, metrics.falsePositiveRate());
        assertEquals(1.0 / 3.0, metrics.falseNegativeRate());
    }

    @Test
    @DisplayName("분류 분모가 0이면 해당 rate를 null로 반환한다")
    void returnsNullRatesWhenDenominatorsAreZero() {
        insertSuite(70_051L);
        insertTestRun(80_051L, 70_051L);
        insertSnapshot(90_051L, 80_051L, 1L, "not evaluated", "input", "BLOCK", "LOW", "PII");
        insertExecution(90_051L, "FAILED", null, "PROVIDER_ERROR", "안전한 오류");

        EvaluatorMetricsView metrics = metricsPort.load(80_051L);

        assertEquals(0L, metrics.truePositive());
        assertEquals(0L, metrics.trueNegative());
        assertEquals(0L, metrics.falsePositive());
        assertEquals(0L, metrics.falseNegative());
        assertNull(metrics.falsePositiveRate());
        assertNull(metrics.falseNegativeRate());
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
                    created_at, updated_at)
                VALUES (?, ?, 'case', 'input', 'BLOCK', 'LOW', 'PII', ?, ?)
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
            long snapshotId, String status, String evaluatorVerdict,
            String errorCode, String errorMessage) {
        jdbcTemplate.update("""
                INSERT INTO test_execution (
                    snapshot_id, result_status, application_response, evaluator_verdict,
                    error_stage, error_code, error_message, started_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, snapshotId, status,
                evaluatorVerdict == null ? null : "stored application response",
                evaluatorVerdict, errorCode == null ? null : "APPLICATION_TARGET", errorCode, errorMessage,
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

    private void insertAttentionFixtures(long suiteId, long testRunId) {
        insertSuite(suiteId);
        insertTestRun(testRunId, suiteId);
        insertSnapshot(90_061L, testRunId, 61L, "fn", "input", "BLOCK", "CRITICAL", "PII");
        insertModernExecution(90_061L, "ALLOW", "response");
        insertSnapshot(90_062L, testRunId, 62L, "failed", "input", "BLOCK", "HIGH", "PII");
        insertExecution(90_062L, "FAILED", null, "PROVIDER_ERROR", "오류");
        insertSnapshot(90_063L, testRunId, 63L, "timeout", "input", "BLOCK", "MEDIUM", "PII");
        insertExecution(90_063L, "TIMED_OUT", null, "PROVIDER_TIMEOUT", "시간 초과");
        insertSnapshot(90_064L, testRunId, 64L, "fp", "input", "ALLOW", "HIGH", "PII");
        insertModernExecution(90_064L, "BLOCK", "response");
        insertSnapshot(90_065L, testRunId, 65L, "not started", "input", "ALLOW", "LOW", "PII");
        insertNotStartedExecution(90_065L);
        insertSnapshot(90_066L, testRunId, 66L, "tp", "input", "BLOCK", "CRITICAL", "PII");
        insertModernExecution(90_066L, "BLOCK", "response");
        insertSnapshot(90_067L, testRunId, 67L, "other timeout", "input", "BLOCK", "CRITICAL", "OTHER");
        insertExecution(90_067L, "TIMED_OUT", null, "PROVIDER_TIMEOUT", "시간 초과");
    }

    private void insertNotStartedExecution(long snapshotId) {
        jdbcTemplate.update("""
                INSERT INTO test_execution (snapshot_id, result_status)
                VALUES (?, 'NOT_STARTED')
                """, snapshotId);
    }

}
