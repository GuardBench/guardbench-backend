package com.guardbench.testrun.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testrun.application.port.out.LoadTestRunDetailPort;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * TestRun 상세 조회의 상태·진행률·targets·Quality Gate 조합을 실제 PostgreSQL에서 검증한다.
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
        insertTestRun(60_001L, 50_001L, "RUNNING", "2", null, null, true, false);

        TestRunDetail detail = port.load(60_001L).orElseThrow();

        assertEquals("RUNNING", detail.status().name());
        assertEquals("target-ref-60001", detail.target().referenceId());
        assertNull(detail.executionOutcome());
        assertNull(detail.qualityGate());
    }

    @Test
    @DisplayName("FINISHED TestRun의 PASS Quality Gate는 전체 metrics를 포함한다")
    void returnsFullMetricsForPassedQualityGate() {
        insertSuite(50_011L);
        insertTestRun(60_011L, 50_011L, "FINISHED", "3", "COMPLETED", null, true, true);
        insertQualityGateResult(60_011L, "PASS");

        TestRunDetail detail = port.load(60_011L).orElseThrow();

        assertEquals("PASS", detail.qualityGate().statusCode());
        assertEquals(0.95, detail.qualityGate().metrics().assertionPassRate());
    }

    @Test
    @DisplayName("NOT_EVALUATED Quality Gate는 metrics가 null이다")
    void returnsNullMetricsForNotEvaluatedQualityGate() {
        insertSuite(50_021L);
        insertTestRun(60_021L, 50_021L, "FINISHED", "4", "ERROR", null, true, true);
        insertQualityGateResult(60_021L, "NOT_EVALUATED");

        TestRunDetail detail = port.load(60_021L).orElseThrow();

        assertEquals("NOT_EVALUATED", detail.qualityGate().statusCode());
        assertNull(detail.qualityGate().metrics());
    }

    private void insertSuite(long id) {
        jdbcTemplate.update("""
                INSERT INTO test_suite (id, name, description, created_at, updated_at)
                VALUES (?, 'Suite', NULL, ?, ?)
                """, id, Timestamp.from(T0), Timestamp.from(T0));
    }

    private void insertTestRun(
            long id, long suiteId, String status, String resolvedVersion, String executionOutcome,
            String unused, boolean started, boolean finished) {
        String targetReference = "target-ref-" + id;
        jdbcTemplate.update("INSERT INTO target_reference(reference_id, target_type) VALUES (?, 'BEDROCK_GUARDRAIL')",
                targetReference);
        jdbcTemplate.update("INSERT INTO bedrock_guardrail_target(reference_id, guardrail_identifier, requested_revision, resolved_revision) VALUES (?, 'guardrail', 'DRAFT', NULL)", targetReference);
        jdbcTemplate.update("""
                INSERT INTO test_run (
                    id, test_suite_id, status, test_case_count, processed_test_case_count,
                    target_reference_id, execution_outcome,
                    created_at, started_at, completed_at, updated_at)
                VALUES (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, suiteId, status, finished ? 1 : 0, targetReference, executionOutcome,
                Timestamp.from(T0),
                started ? Timestamp.from(T0) : null,
                finished ? Timestamp.from(T0) : null,
                Timestamp.from(T0));
    }

    private void insertQualityGateResult(long testRunId, String status) {
        boolean evaluated = !"NOT_EVALUATED".equals(status);
        jdbcTemplate.update("""
                INSERT INTO quality_gate_result (
                    test_run_id, gate_status, assertion_pass_rate, execution_success_rate, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                testRunId, status,
                evaluated ? 0.95 : null, evaluated ? 0.98 : null, Timestamp.from(T0));
    }
}
