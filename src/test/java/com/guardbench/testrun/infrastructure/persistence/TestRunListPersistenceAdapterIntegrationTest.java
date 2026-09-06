package com.guardbench.testrun.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

import com.guardbench.testrun.application.port.out.LoadTestRunListPort;
import com.guardbench.testrun.application.port.out.PageCriteria;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.SortOrder;
import com.guardbench.testrun.application.port.out.TestRunListCriteria;
import com.guardbench.testrun.application.port.out.TestRunListItem;
import com.guardbench.testrun.application.port.out.TestRunListSortField;
import com.guardbench.testrun.domain.TestRunExecutionOutcome;
import com.guardbench.testrun.domain.TestRunStatus;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * TestRun 목록 filter, Quality Gate 상태 JOIN, 정렬과 Offset Pagination을 실제 PostgreSQL에서
 * 검증한다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1</a>
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class TestRunListPersistenceAdapterIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-25T11:00:00Z");

    @Autowired
    private LoadTestRunListPort port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Quality Gate 상태로 filter하면 quality_gate_result가 없는 TestRun은 제외한다")
    void excludesTestRunsWithoutQualityGateResultWhenFilteringByStatus() {
        insertSuite(30_001L);
        insertTestRun(40_001L, 30_001L, TestRunStatus.FINISHED, TestRunExecutionOutcome.COMPLETED, T0);
        insertTestRun(40_002L, 30_001L, TestRunStatus.RUNNING, null, T0);
        insertQualityGateResult(40_001L, "PASS");

        TestRunListCriteria criteria = new TestRunListCriteria(
                null, Set.of(), Set.of(), Set.of("PASS"), null, null, List.of(), PageCriteria.firstPage());

        PageResult<TestRunListItem> result = port.load(criteria);

        assertEquals(List.of(40_001L), result.items().stream().map(TestRunListItem::id).toList());
        TestRunListItem item = result.items().getFirst();
        assertEquals("PASS", item.qualityGateStatusCode());
        assertEquals(1.0, item.qualityGateMetrics().assertion().value());
        assertEquals(0.95, item.qualityGateMetrics().assertion().threshold());
        assertTrue(item.qualityGateMetrics().assertion().passed());
        assertEquals(1.0, item.qualityGateMetrics().execution().value());
        assertEquals(0.95, item.qualityGateMetrics().execution().threshold());
        assertTrue(item.qualityGateMetrics().execution().passed());
    }

    @Test
    @DisplayName("Quality Gate 상태 filter를 생략하면 quality_gate_result가 없는 TestRun도 포함하고 코드는 null이다")
    void includesTestRunsWithoutQualityGateResultWhenNoStatusFilter() {
        insertSuite(30_011L);
        insertTestRun(40_011L, 30_011L, TestRunStatus.RUNNING, null, T0);

        PageResult<TestRunListItem> result = port.load(TestRunListCriteria.firstPage());

        assertEquals(1, result.items().size());
        assertNull(result.items().getFirst().qualityGateStatusCode());
        assertNull(result.items().getFirst().qualityGateMetrics());
    }

    @Test
    @DisplayName("NOT_EVALUATED Quality Gate는 목록 metric evidence를 null로 반환한다")
    void returnsNullMetricsForNotEvaluatedQualityGate() {
        insertSuite(30_012L);
        insertTestRun(40_012L, 30_012L, TestRunStatus.FINISHED, TestRunExecutionOutcome.INCOMPLETE, T0);
        insertQualityGateResult(40_012L, "NOT_EVALUATED");

        TestRunListItem item = port.load(TestRunListCriteria.firstPage()).items().getFirst();

        assertEquals("NOT_EVALUATED", item.qualityGateStatusCode());
        assertNull(item.qualityGateMetrics());
    }

    @Test
    @DisplayName("생성 시각 내림차순 정렬과 진행률 계산이 계약대로 동작한다")
    void sortsByCreatedAtDescendingAndComputesProgressPercent() {
        insertSuite(30_021L);
        insertTestRun(40_021L, 30_021L, TestRunStatus.RUNNING, null, T0);
        insertTestRun(40_022L, 30_021L, TestRunStatus.RUNNING, null, T1);
        jdbcTemplate.update(
                "UPDATE test_run SET processed_test_case_count = 5, test_case_count = 10 WHERE id = ?", 40_021L);

        PageResult<TestRunListItem> result = port.load(new TestRunListCriteria(
                null, Set.of(), Set.of(), Set.of(), null, null,
                List.of(SortOrder.desc(TestRunListSortField.CREATED_AT)), PageCriteria.firstPage()));

        assertEquals(List.of(40_022L, 40_021L), result.items().stream().map(TestRunListItem::id).toList());
        TestRunListItem partiallyProcessed = result.items().get(1);
        assertEquals(5L, partiallyProcessed.progress().processedTestCaseCount());
        assertEquals(50.0, partiallyProcessed.progress().percent());
    }

    @Test
    @DisplayName("범위를 초과한 유효한 페이지는 빈 목록과 요청 페이지 번호를 유지한다")
    void returnsEmptyItemsForOutOfRangeValidPage() {
        insertSuite(30_031L);
        insertTestRun(40_031L, 30_031L, TestRunStatus.QUEUED, null, T0);

        PageResult<TestRunListItem> result = port.load(new TestRunListCriteria(
                null, Set.of(), Set.of(), Set.of(), null, null, List.of(), new PageCriteria(2, 20)));

        assertTrue(result.items().isEmpty());
        assertEquals(2, result.number());
        assertEquals(1L, result.totalElements());
    }

    private void insertSuite(long id) {
        jdbcTemplate.update("""
                INSERT INTO test_suite (id, name, description, created_at, updated_at)
                VALUES (?, 'Suite', NULL, ?, ?)
                """, id, Timestamp.from(T0), Timestamp.from(T0));
    }

    private void insertTestRun(
            long id, long suiteId, TestRunStatus status, TestRunExecutionOutcome outcome, Instant createdAt) {
        boolean finished = status == TestRunStatus.FINISHED;
        boolean started = status != TestRunStatus.QUEUED;
        String targetReference = "target-ref-" + id;
        String evaluatorReference = "evaluator-ref-" + id;
        jdbcTemplate.update("INSERT INTO target_reference(reference_id, target_type) VALUES (?, 'HTTP_ENDPOINT')",
                targetReference);
        jdbcTemplate.update("""
                INSERT INTO http_endpoint_target(reference_id, endpoint_url, model)
                VALUES (?, 'https://example.com/v1/chat/completions', 'test-model')
                """, targetReference);
        jdbcTemplate.update("""
                INSERT INTO evaluator_reference(reference_id, provider_code, model_id)
                VALUES (?, 'SAGEMAKER', 'classifier-endpoint')
                """, evaluatorReference);
        jdbcTemplate.update("""
                INSERT INTO test_run (
                    id, test_suite_id, status, test_case_count, processed_test_case_count,
                    target_reference_id, evaluator_reference_id, execution_outcome,
                    created_at, started_at, completed_at, updated_at)
                VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, suiteId, status.name(), finished ? 1 : 0,
                targetReference, evaluatorReference,
                outcome == null ? null : outcome.name(),
                Timestamp.from(createdAt),
                started ? Timestamp.from(createdAt) : null,
                finished ? Timestamp.from(createdAt) : null,
                Timestamp.from(createdAt));
    }

    private void insertQualityGateResult(long testRunId, String status) {
        boolean evaluated = !"NOT_EVALUATED".equals(status);
        jdbcTemplate.update("""
                INSERT INTO quality_gate_result (
                    test_run_id, gate_status,
                    assertion_pass_rate, assertion_pass_rate_threshold, assertion_passed,
                    execution_success_rate, execution_success_rate_threshold, execution_passed,
                    created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                testRunId, status,
                evaluated ? 1.0 : null, evaluated ? 0.95 : null, evaluated ? true : null,
                evaluated ? 1.0 : null, evaluated ? 0.95 : null, evaluated ? true : null,
                Timestamp.from(T0));
    }
}
