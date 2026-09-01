package com.guardbench.evaluation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
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
 * 최종화 동시성 통합 테스트다.
 *
 * <p>ADR 0005: 동시 완료 메시지가 도착해도 TestRun 행 잠금으로 직렬화되어
 * SnapshotEvaluation과 QualityGateResult가 한 번만 생성되고
 * TestRun은 FINISHED로 한 번만 전이해야 한다.
 */
@SpringBootTest
@Import({PostgresTestConfiguration.class,
        TestRunFinalizationConcurrencyIntegrationTest.FinalizationTestConfiguration.class})
class TestRunFinalizationConcurrencyIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final long TEST_SUITE_ID = 800L;
    private static final long TEST_CASE_ID = 810L;
    private static final long TEST_RUN_ID = 820L;
    private static final long SNAPSHOT_ID = 830L;

    @TestConfiguration(proxyBeanMethods = false)
    static class FinalizationTestConfiguration {

        /**
         * Worker 설정 전체를 로딩하지 않고 최종화 서비스만 운영 구성과 동일하게 조립한다.
         * {@code @Transactional} 프록시와 실제 Port 구현을 그대로 사용한다.
         */
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
    void setUpRunningTestRun() {
        TestRunPersistenceFixture fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.clearPersistenceTables();
        fixture.insertTestSuite(TEST_SUITE_ID, NOW);
        fixture.insertTestCase(TEST_CASE_ID, TEST_SUITE_ID, NOW);
        fixture.insertQueuedTestRun(TEST_RUN_ID, TEST_SUITE_ID, 1, NOW);
        fixture.insertSnapshot(SNAPSHOT_ID, TEST_RUN_ID, TEST_CASE_ID, NOW);

        jdbcTemplate.update("""
                UPDATE test_run
                SET status = 'RUNNING', started_at = ?
                WHERE id = ?
                """, Timestamp.from(NOW), TEST_RUN_ID);
        insertSucceededExecution();
    }

    @Test
    @DisplayName("최종화 서비스는 트랜잭션 프록시로 등록된다")
    void finalizationServiceIsTransactionalProxy() {
        assertThat(AopUtils.isAopProxy(finalizeTestRunService))
                .as("최종화 전체가 하나의 트랜잭션에서 실행돼야 한다")
                .isTrue();
    }

    @Test
    @DisplayName("동시 완료 메시지에서도 Quality Gate와 평가는 한 번만 생성된다")
    void concurrentFinalizationCreatesSingleResult() throws InterruptedException {
        int workers = 4;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(workers);
        List<FinalizationOutcome> outcomes = new CopyOnWriteArrayList<>();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        List<Thread> threads = new ArrayList<>();

        for (int index = 0; index < workers; index++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await();
                    outcomes.add(finalizeTestRunService.finalize(TEST_RUN_ID));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (Throwable throwable) {
                    failures.add(throwable);
                } finally {
                    done.countDown();
                }
            });
            threads.add(thread);
            thread.start();
        }

        start.countDown();
        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(failures).isEmpty();
        assertThat(outcomes).hasSize(workers);
        long finalizedCount = outcomes.stream()
                .filter(FinalizationOutcome.Finalized.class::isInstance)
                .count();
        long alreadyFinalizedCount = outcomes.stream()
                .filter(FinalizationOutcome.AlreadyFinalized.class::isInstance)
                .count();

        assertThat(finalizedCount)
                .as("최종화는 정확히 한 번만 수행돼야 한다")
                .isEqualTo(1);
        assertThat(alreadyFinalizedCount)
                .as("나머지 완료 메시지는 멱등 성공으로 수렴해야 한다")
                .isEqualTo(workers - 1L);

        assertThat(countOf("quality_gate_result")).isEqualTo(1);
        assertThat(countOf("assertion_result")).isEqualTo(1);
        assertThat(testRunStatus()).isEqualTo("FINISHED");
        assertThat(processedTestCaseCount()).isEqualTo(1);
    }

    // ─── Helper ──────────────────────────────────────────────────────

    private void insertSucceededExecution() {
        jdbcTemplate.update("""
                INSERT INTO test_execution(snapshot_id, result_status, application_response, evaluator_verdict,
                    started_at, completed_at)
                VALUES (?, 'SUCCEEDED', 'stored application response', 'ALLOW', ?, ?)
                """, SNAPSHOT_ID, Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private int countOf(String table) {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Integer.class);
        return count == null ? 0 : count;
    }

    private String testRunStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM test_run WHERE id = ?", String.class, TEST_RUN_ID);
    }

    private int processedTestCaseCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT processed_test_case_count FROM test_run WHERE id = ?", Integer.class, TEST_RUN_ID);
        return count == null ? 0 : count;
    }
}
