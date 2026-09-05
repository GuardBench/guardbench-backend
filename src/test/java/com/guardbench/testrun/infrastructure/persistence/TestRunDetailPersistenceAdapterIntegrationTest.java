package com.guardbench.testrun.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testrun.application.port.out.LoadTestRunDetailPort;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * TestRun 상세 조회의 상태·진행률·target·Quality Gate 조합을 실제 PostgreSQL에서 검증한다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1</a>
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class TestRunDetailPersistenceAdapterIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-08-25T10:00:00Z");

    @Autowired
    private LoadTestRunDetailPort port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("존재하지 않는 TestRun을 조회하면 빈 결과를 반환한다")
    void returnsEmptyWhenTestRunDoesNotExist() {
        Optional<TestRunDetail> result = port.load(999_999L);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("RUNNING TestRun은 Quality Gate가 null이고 target reference를 포함한다")
    void returnsNullQualityGateForRunningTestRun() {
        insertSuite(50_001L);
        insertTestRun(60_001L, 50_001L, "RUNNING", "2", null, true, false);

        TestRunDetail detail = port.load(60_001L).orElseThrow();

        assertEquals("RUNNING", detail.status().name());
        assertEquals("target-ref-60001", detail.target().referenceId());
        assertEquals("HTTP_ENDPOINT", detail.target().type());
        assertEquals("https://example.com/v1/chat/completions", detail.target().identifier());
        assertNull(detail.executionOutcome());
        assertNull(detail.qualityGate());
    }

    @Test
    @DisplayName("FINISHED TestRun의 PASS Quality Gate는 전체 metrics를 포함한다")
    void returnsFullMetricsForPassedQualityGate() {
        insertSuite(50_011L);
        insertTestRun(60_011L, 50_011L, "FINISHED", "3", "COMPLETED", true, true);
        insertQualityGateResult(60_011L, "PASS");

        TestRunDetail detail = port.load(60_011L).orElseThrow();

        assertEquals("PASS", detail.qualityGate().statusCode());
        assertEquals(0.95, detail.qualityGate().metrics().assertionPassRate());
        assertEquals(0.95, detail.qualityGate().metrics().assertion().threshold());
        assertTrue(detail.qualityGate().metrics().assertion().passed());
        assertEquals(0.98, detail.qualityGate().metrics().execution().value());
        assertEquals(0.95, detail.qualityGate().metrics().execution().threshold());
        assertTrue(detail.qualityGate().metrics().execution().passed());
    }

    @Test
    @DisplayName("NOT_EVALUATED Quality Gate는 metrics가 null이다")
    void returnsNullMetricsForNotEvaluatedQualityGate() {
        insertSuite(50_021L);
        insertTestRun(60_021L, 50_021L, "FINISHED", "4", "ERROR", true, true);
        insertQualityGateResult(60_021L, "NOT_EVALUATED");

        TestRunDetail detail = port.load(60_021L).orElseThrow();

        assertEquals("NOT_EVALUATED", detail.qualityGate().statusCode());
        assertNull(detail.qualityGate().metrics());
    }

    @Test
    @DisplayName("Assertion 판정과 value/threshold가 모순되면 DB가 거부한다")
    void rejectsContradictoryAssertionDecision() {
        insertSuite(50_031L);
        insertTestRun(60_031L, 50_031L, "FINISHED", "5", "COMPLETED", true, true);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertQualityGateResult(60_031L, "FAIL", 0.95, 0.95, false, 0.98, 0.95, true));
    }

    @Test
    @DisplayName("Execution 판정과 value/threshold가 모순되면 DB가 거부한다")
    void rejectsContradictoryExecutionDecision() {
        insertSuite(50_041L);
        insertTestRun(60_041L, 50_041L, "FINISHED", "6", "COMPLETED", true, true);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertQualityGateResult(60_041L, "FAIL", 0.95, 0.95, true, 0.98, 0.95, false));
    }

    @Test
    @DisplayName("최종 상태와 metric별 판정이 모순되면 DB가 거부한다")
    void rejectsStatusContradictingMetricDecisions() {
        insertSuite(50_051L);
        insertTestRun(60_051L, 50_051L, "FINISHED", "7", "COMPLETED", true, true);

        assertThrows(DataIntegrityViolationException.class,
                () -> insertQualityGateResult(60_051L, "PASS", 0.90, 0.95, false, 0.98, 0.95, true));
    }

    private void insertSuite(long id) {
        jdbcTemplate.update("""
                INSERT INTO test_suite (id, name, description, created_at, updated_at)
                VALUES (?, 'Suite', NULL, ?, ?)
                """, id, Timestamp.from(T0), Timestamp.from(T0));
    }

    private void insertTestRun(
            long id, long suiteId, String status, String requestedRevision, String executionOutcome,
            boolean started, boolean finished) {
        String targetReference = "target-ref-" + id;
        String evaluatorReference = "evaluator-ref-" + id;
        jdbcTemplate.update("INSERT INTO target_reference(reference_id, target_type) VALUES (?, 'HTTP_ENDPOINT')",
                targetReference);
        jdbcTemplate.update("""
                INSERT INTO http_endpoint_target(reference_id, endpoint_url, model, requested_revision)
                VALUES (?, 'https://example.com/v1/chat/completions', 'test-model', ?)
                """, targetReference, requestedRevision);
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
                id, suiteId, status, finished ? 1 : 0, targetReference, evaluatorReference, executionOutcome,
                Timestamp.from(T0),
                started ? Timestamp.from(T0) : null,
                finished ? Timestamp.from(T0) : null,
                Timestamp.from(T0));
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
                evaluated ? 0.95 : null, evaluated ? 0.95 : null, evaluated ? true : null,
                evaluated ? 0.98 : null, evaluated ? 0.95 : null, evaluated ? true : null,
                Timestamp.from(T0));
    }

    private void insertQualityGateResult(
            long testRunId,
            String status,
            double assertionValue,
            double assertionThreshold,
            boolean assertionPassed,
            double executionValue,
            double executionThreshold,
            boolean executionPassed) {
        jdbcTemplate.update("""
                INSERT INTO quality_gate_result (
                    test_run_id, gate_status,
                    assertion_pass_rate, assertion_pass_rate_threshold, assertion_passed,
                    execution_success_rate, execution_success_rate_threshold, execution_passed,
                    created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                testRunId, status,
                assertionValue, assertionThreshold, assertionPassed,
                executionValue, executionThreshold, executionPassed,
                Timestamp.from(T0));
    }
}
