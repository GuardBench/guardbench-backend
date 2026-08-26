package com.guardbench.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.evaluation.application.FinalizeTestRunService.FinalizationOutcome;
import com.guardbench.evaluation.application.port.out.FinalizeTestRunPort;
import com.guardbench.evaluation.application.port.out.LoadTestRunExecutionFactsPort;
import com.guardbench.evaluation.domain.QualityGateEvaluator;
import com.guardbench.evaluation.domain.SnapshotEvaluator;
import com.guardbench.evaluation.domain.repository.QualityGateResultRepository;
import com.guardbench.evaluation.domain.repository.SnapshotEvaluationRepository;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * 부분 완료 시 RUNNING 진행률 저장 통합 테스트다.
 *
 * <p>ADR 0005 4단계·OpenAPI 진행률 계약: 모든 pair가 terminal이 아니어도
 * 처리 완료된 pair 수만큼 {@code processed_test_case_count}를 갱신해야
 * 목록·상세 조회가 실제 진행 상황을 보여줄 수 있다.
 */
@SpringBootTest
@Import({PostgresTestConfiguration.class,
        PartialCompletionProgressIntegrationTest.FinalizationTestConfiguration.class})
class PartialCompletionProgressIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final long TEST_SUITE_ID = 900L;
    private static final long TEST_CASE_ID_1 = 910L;
    private static final long TEST_CASE_ID_2 = 911L;
    private static final long TEST_RUN_ID = 920L;
    private static final long SNAPSHOT_ID_1 = 930L;
    private static final long SNAPSHOT_ID_2 = 931L;

    @TestConfiguration(proxyBeanMethods = false)
    static class FinalizationTestConfiguration {

        @Bean
        FinalizeTestRunService finalizeTestRunService(
                LoadTestRunExecutionFactsPort loadExecutionFactsPort,
                FinalizeTestRunPort finalizeTestRunPort,
                QualityGateResultRepository qualityGateResultRepository,
                SnapshotEvaluationRepository snapshotEvaluationRepository,
                Clock clock
        ) {
            return new FinalizeTestRunService(
                    loadExecutionFactsPort,
                    finalizeTestRunPort,
                    qualityGateResultRepository,
                    snapshotEvaluationRepository,
                    new SnapshotEvaluator(),
                    new QualityGateEvaluator(),
                    clock
            );
        }
    }

    @Autowired
    private FinalizeTestRunService finalizeTestRunService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpRunningTestRunWithTwoSnapshots() {
        TestRunPersistenceFixture fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.clearPersistenceTables();
        fixture.insertTestSuite(TEST_SUITE_ID, NOW);
        fixture.insertTestCase(TEST_CASE_ID_1, TEST_SUITE_ID, NOW);
        fixture.insertTestCase(TEST_CASE_ID_2, TEST_SUITE_ID, NOW);
        fixture.insertQueuedTestRun(TEST_RUN_ID, TEST_SUITE_ID, 2, NOW);
        fixture.insertSnapshot(SNAPSHOT_ID_1, TEST_RUN_ID, TEST_CASE_ID_1, NOW);
        fixture.insertSnapshot(SNAPSHOT_ID_2, TEST_RUN_ID, TEST_CASE_ID_2, NOW);

        jdbcTemplate.update("""
                UPDATE test_run
                SET status = 'RUNNING', started_at = ?, candidate_resolved_version = '2'
                WHERE id = ?
                """, Timestamp.from(NOW), TEST_RUN_ID);
    }

    @Test
    @DisplayName("한 Snapshot만 완료돼도 RUNNING 진행도가 갱신된다")
    void updatesProgressWhenOnlyOneSnapshotIsComplete() {
        insertSucceededExecution(SNAPSHOT_ID_1, "BASELINE");
        insertSucceededExecution(SNAPSHOT_ID_1, "CANDIDATE");
        // SNAPSHOT_ID_2는 아직 실행 결과가 없다.

        FinalizationOutcome outcome = finalizeTestRunService.finalize(TEST_RUN_ID);

        assertThat(outcome).isInstanceOf(FinalizationOutcome.NotReady.class);
        assertThat(testRunStatus()).isEqualTo("RUNNING");
        assertThat(processedTestCaseCount())
                .as("완료된 Snapshot 1건만큼 진행도가 갱신돼야 한다")
                .isEqualTo(1);
        assertThat(qualityGateResultCount()).isZero();
    }

    @Test
    @DisplayName("Snapshot이 아직 fan-out되지 않았으면 진행도를 갱신하지 않는다")
    void doesNotUpdateProgressWhenSnapshotsAreIncomplete() {
        // SNAPSHOT_ID_2는 fixture에서 이미 생성됐지만, 이 테스트는 testCaseCount만큼
        // Snapshot이 아직 갖춰지지 않은 초기 fan-out 시점을 시뮬레이션한다.
        jdbcTemplate.update("UPDATE test_run SET test_case_count = 3 WHERE id = ?", TEST_RUN_ID);
        insertSucceededExecution(SNAPSHOT_ID_1, "BASELINE");
        insertSucceededExecution(SNAPSHOT_ID_1, "CANDIDATE");

        FinalizationOutcome outcome = finalizeTestRunService.finalize(TEST_RUN_ID);

        assertThat(outcome).isInstanceOf(FinalizationOutcome.NotReady.class);
        assertThat(processedTestCaseCount())
                .as("Snapshot이 testCaseCount만큼 준비되지 않으면 진행도를 갱신하지 않는다")
                .isZero();
    }

    // ─── Helper ──────────────────────────────────────────────────────

    private void insertSucceededExecution(long snapshotId, String targetType) {
        jdbcTemplate.update("""
                INSERT INTO test_execution(snapshot_id, target_type, result_status, actual_action, started_at, completed_at)
                VALUES (?, ?, 'SUCCEEDED', 'ALLOW', ?, ?)
                """, snapshotId, targetType, Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private String testRunStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM test_run WHERE id = ?", String.class, TEST_RUN_ID);
    }

    private int processedTestCaseCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT processed_test_case_count FROM test_run WHERE id = ?", Integer.class, TEST_RUN_ID);
        return count == null ? 0 : count;
    }

    private int qualityGateResultCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM quality_gate_result", Integer.class);
        return count == null ? 0 : count;
    }
}
