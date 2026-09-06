package com.guardbench.testrun.infrastructure.integration.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.guardbench.evaluation.application.FinalizeTestRunService;
import com.guardbench.evaluation.application.port.out.FinalizeTestRunPort;
import com.guardbench.evaluation.application.port.out.LoadTestRunExecutionFactsPort;
import com.guardbench.evaluation.domain.repository.QualityGateResultRepository;
import com.guardbench.evaluation.domain.repository.SnapshotEvaluationRepository;
import com.guardbench.testrun.application.CheckTestRunCompletionService;
import com.guardbench.testrun.application.port.in.HandleTestExecutionCompletedPort;
import com.guardbench.testrun.domain.repository.TestCaseSnapshotRepository;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

/** 실제 Worker handler와 PostgreSQL을 사용하며 SQS transport는 별도 reliability suite가 검증한다. */
@SpringBootTest
@Import({PostgresTestConfiguration.class, CompletionFanInIntegrationTest.Configuration.class})
class CompletionFanInIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-06T00:00:00Z");
    private static final long RUN_ID = 920L;

    @TestConfiguration(proxyBeanMethods = false)
    static class Configuration {
        @Bean
        FinalizeTestRunService finalizer(LoadTestRunExecutionFactsPort facts, FinalizeTestRunPort finish,
                QualityGateResultRepository quality, SnapshotEvaluationRepository snapshots, Clock clock) {
            return new EvaluationFinalizationWorkerConfiguration()
                    .finalizeTestRunService(facts, finish, quality, snapshots, clock);
        }

        @Bean
        HandleTestExecutionCompletedPort handler(FinalizeTestRunService finalizer,
                CheckTestRunCompletionService readiness) {
            return new EvaluationFinalizationWorkerConfiguration()
                    .handleTestExecutionCompletedPort(finalizer, readiness);
        }
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private HandleTestExecutionCompletedPort handler;
    @MockitoSpyBean private TestCaseSnapshotRepository snapshots;
    @MockitoSpyBean private TestExecutionRepository executions;
    @MockitoSpyBean private QualityGateResultRepository quality;

    @BeforeEach
    void clearTables() {
        new TestRunPersistenceFixture(jdbc).clearPersistenceTables();
    }

    @ParameterizedTest
    @ValueSource(ints = {78, 491})
    @DisplayName("대형 Run의 부분 완료와 종료 후 중복 메시지는 실행 전문을 조회하지 않는다")
    void partialAndDuplicateCompletionsAvoidFullFacts(int count) {
        createRun(count);
        for (int i = 0; i < count - 1; i++) {
            complete(i);
            assertThat(handler.handle(RUN_ID)).isTrue();
        }
        assertThat(handler.handle(RUN_ID)).isTrue();
        assertThat(value("processed_test_case_count", Integer.class)).isEqualTo(count - 1);
        assertThat(value("status", String.class)).isEqualTo("RUNNING");
        assertThat(qualityCount()).isZero();
        verify(snapshots, never()).findAllByTestRunId(any());
        verify(executions, never()).findById(any());

        complete(count - 1);
        assertThat(handler.handle(RUN_ID)).isTrue();
        assertThat(value("status", String.class)).isEqualTo("FINISHED");
        assertThat(value("processed_test_case_count", Integer.class)).isEqualTo(count);
        assertThat(qualityCount()).isEqualTo(1);

        clearInvocations(snapshots, executions);
        for (int i = 0; i < count; i++) {
            assertThat(handler.handle(RUN_ID)).isTrue();
        }
        verify(snapshots, never()).findAllByTestRunId(any());
        verify(executions, never()).findById(any());
        assertThat(qualityCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("동시 완료와 중복 전달은 Quality Gate 한 건과 FINISHED로 수렴한다")
    void concurrentCompletionsFinalizeOnce() throws Exception {
        createRun(16);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<Boolean>>();
            for (int i = 0; i < 16; i++) {
                int index = i;
                tasks.add(() -> { complete(index); return handler.handle(RUN_ID); });
            }
            for (var result : executor.invokeAll(tasks, 30, TimeUnit.SECONDS)) {
                assertThat(result.get()).isTrue();
            }
        }
        assertThat(value("status", String.class)).isEqualTo("FINISHED");
        assertThat(qualityCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM assertion_result", Integer.class)).isEqualTo(16);
    }

    @Test
    @DisplayName("readiness 확인 후 최종화 저장 실패는 롤백되고 재전달로 완료된다")
    void finalizationFailureRetriesAfterReadiness() {
        createRun(1);
        complete(0);
        doThrow(new IllegalStateException("storage unavailable")).when(quality).save(any());

        assertThatThrownBy(() -> handler.handle(RUN_ID)).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(value("status", String.class)).isEqualTo("RUNNING");
        assertThat(qualityCount()).isZero();

        reset(quality);
        assertThat(handler.handle(RUN_ID)).isTrue();
        assertThat(value("status", String.class)).isEqualTo("FINISHED");
        assertThat(qualityCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("실패 실행도 terminal로 집계해 INCOMPLETE로 종료한다")
    void failedExecutionCountsAsTerminal() {
        createRun(2);
        complete(0);
        jdbc.update("""
                INSERT INTO test_execution(snapshot_id, result_status, error_stage, error_code,
                    error_message, started_at, completed_at)
                VALUES (10001, 'FAILED', 'APPLICATION_TARGET', 'PROVIDER_UNAVAILABLE', 'failure', ?, ?)
                """, Timestamp.from(NOW), Timestamp.from(NOW));

        assertThat(handler.handle(RUN_ID)).isTrue();
        assertThat(value("execution_outcome", String.class)).isEqualTo("INCOMPLETE");
        assertThat(qualityCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Snapshot 준비가 불완전하면 진행도를 변경하거나 조기 종료하지 않는다")
    void missingSnapshotsDoNotFinalize() {
        createRun(1);
        complete(0);
        jdbc.update("UPDATE test_run SET test_case_count = 2 WHERE id = ?", RUN_ID);

        assertThat(handler.handle(RUN_ID)).isTrue();
        assertThat(value("processed_test_case_count", Integer.class)).isZero();
        assertThat(value("status", String.class)).isEqualTo("RUNNING");
        assertThat(qualityCount()).isZero();
    }

    private void createRun(int count) {
        var fixture = new TestRunPersistenceFixture(jdbc);
        fixture.insertTestSuite(900L, NOW);
        fixture.insertQueuedTestRun(RUN_ID, 900L, count, NOW);
        for (int i = 0; i < count; i++) {
            fixture.insertTestCase(20000L + i, 900L, NOW);
            fixture.insertSnapshot(10000L + i, RUN_ID, 20000L + i, NOW);
        }
        jdbc.update("UPDATE test_run SET status = 'RUNNING', started_at = ? WHERE id = ?",
                Timestamp.from(NOW), RUN_ID);
        clearInvocations(snapshots, executions);
    }

    private void complete(int index) {
        jdbc.update("""
                INSERT INTO test_execution(snapshot_id, result_status, application_response,
                    evaluator_verdict, started_at, completed_at)
                VALUES (?, 'SUCCEEDED', 'response', 'ALLOW', ?, ?)
                """, 10000L + index, Timestamp.from(NOW), Timestamp.from(NOW));
    }

    private <T> T value(String column, Class<T> type) {
        return jdbc.queryForObject("SELECT " + column + " FROM test_run WHERE id = ?", type, RUN_ID);
    }

    private int qualityCount() {
        return jdbc.queryForObject("SELECT count(*) FROM quality_gate_result", Integer.class);
    }
}
