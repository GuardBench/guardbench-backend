package com.guardbench.testrun.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.ExecutionClaimPort;
import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;
import com.guardbench.testrun.application.port.out.TargetPreparationPort;
import com.guardbench.testrun.application.port.out.TargetProviderException;
import com.guardbench.testrun.application.port.out.LoadExecutionContextPort;
import com.guardbench.testrun.application.port.out.LoadSnapshotIdsByTestRunPort;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.ResolutionClaimPort;
import com.guardbench.testrun.application.port.out.SaveNotEvaluatedQualityGatePort;
import com.guardbench.testrun.application.port.out.TransactionalPhasePort;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;
import com.guardbench.testrun.domain.repository.TestRunRepository;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * Worker persistence phase의 원자성 통합 테스트다.
 *
 * <p>ADR 0004/0005: phase 안에서 후속 쓰기가 실패하면 앞선 쓰기도 rollback되어야 한다.
 * 재전달이 앞 상태만 보고 이미 처리된 것으로 판단해 ack하는 상황을 막는다.
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
class WorkerPhaseTransactionIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final long TEST_SUITE_ID = 700L;
    private static final long TEST_CASE_ID = 710L;
    private static final long TEST_RUN_ID = 720L;
    private static final long SNAPSHOT_ID = 730L;

    @Autowired
    private TestRunRepository testRunRepository;

    @Autowired
    private TestExecutionRepository testExecutionRepository;

    @Autowired
    private LoadSnapshotIdsByTestRunPort loadSnapshotIdsPort;

    @Autowired
    private LoadExecutionContextPort loadExecutionContextPort;

    @Autowired
    private SaveNotEvaluatedQualityGatePort saveNotEvaluatedQualityGatePort;

    @Autowired
    private TransactionalPhasePort transactionalPhasePort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpFixture() {
        TestRunPersistenceFixture fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.clearPersistenceTables();
        fixture.insertTestSuite(TEST_SUITE_ID, NOW);
        fixture.insertTestCase(TEST_CASE_ID, TEST_SUITE_ID, NOW);
        fixture.insertQueuedTestRun(TEST_RUN_ID, TEST_SUITE_ID, 1, NOW);
        fixture.insertSnapshot(SNAPSHOT_ID, TEST_RUN_ID, TEST_CASE_ID, NOW);
    }

    @Test
    @DisplayName("fan-out Outbox 저장이 실패하면 RUNNING 전이도 rollback된다")
    void runningTransitionRollsBackWhenFanOutFails() {
        ResolveTestRunService service = resolveService(
                new FailingOutboxPort(),
                saveNotEvaluatedQualityGatePort,
                request -> { },
                1
        );

        assertThatThrownBy(() -> service.resolve(TEST_RUN_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(testRunStatus())
                .as("PREPARING까지만 commit되고 RUNNING 전이는 rollback돼야 한다")
                .isEqualTo("PREPARING");
        assertThat(outboxCount()).isZero();
    }

    @Test
    @DisplayName("준비 실패 종결에서 Quality Gate 저장이 실패하면 NOT_STARTED 실행과 FINISHED 전이도 rollback된다")
    void preparationFailureTerminationIsAtomic() {
        ResolveTestRunService service = resolveService(
                outboxPortSpy(),
                testRunId -> {
                    throw new IllegalStateException("simulated quality gate failure");
                },
                request -> {
                    throw new TargetProviderException(TargetFailureCode.TARGET_NOT_FOUND);
                },
                3
        );

        assertThatThrownBy(() -> service.resolve(TEST_RUN_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(testRunStatus())
                .as("종결 phase 전체가 rollback되어 FINISHED로 전이되지 않아야 한다")
                .isEqualTo("PREPARING");
        assertThat(executionCount()).isZero();
        assertThat(qualityGateCount()).isZero();
    }

    @Test
    @DisplayName("완료 Outbox 저장이 실패하면 terminal TestExecution 저장도 rollback된다")
    void terminalExecutionRollsBackWhenCompletedOutboxFails() {
        ExecuteTestRunService service = new ExecuteTestRunService(
                new StubExecutionClaimPort(),
                testExecutionRepository,
                loadExecutionContextPort,
                request -> TargetExecutionResult.succeeded("ALLOW"),
                new FailingOutboxPort(),
                transactionalPhasePort,
                FIXED_CLOCK
        );

        assertThatThrownBy(() -> service.execute(SNAPSHOT_ID))
                .isInstanceOf(IllegalStateException.class);

        assertThat(executionCount())
                .as("Outbox 저장 실패 시 terminal TestExecution도 남지 않아야 한다")
                .isZero();
        assertThat(outboxCount()).isZero();
    }

    // ─── Helper ──────────────────────────────────────────────────────

    private ResolveTestRunService resolveService(
            OutboxPort outboxPort,
            SaveNotEvaluatedQualityGatePort qualityGatePort,
            TargetPreparationPort preparationPort,
            int attemptCount
    ) {
        return new ResolveTestRunService(
                new StubResolutionClaimPort(attemptCount),
                testRunRepository,
                preparationPort,
                loadSnapshotIdsPort,
                outboxPort,
                testExecutionRepository,
                qualityGatePort,
                transactionalPhasePort,
                FIXED_CLOCK
        );
    }

    private OutboxPort outboxPortSpy() {
        return new OutboxPort() {
            @Override public void save(OutboxEventRecord event) { }
            @Override public List<OutboxEventRecord> findPendingBatch(int batchSize) { return List.of(); }
            @Override public void markPublished(Collection<UUID> eventIds) { }
        };
    }

    private String testRunStatus() {
        return jdbcTemplate.queryForObject("SELECT status FROM test_run WHERE id = ?", String.class, TEST_RUN_ID);
    }

    private int outboxCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM outbox_event", Integer.class);
        return count == null ? 0 : count;
    }

    private int executionCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM test_execution", Integer.class);
        return count == null ? 0 : count;
    }

    private int qualityGateCount() {
        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM quality_gate_result", Integer.class);
        return count == null ? 0 : count;
    }

    private static final class FailingOutboxPort implements OutboxPort {
        @Override
        public void save(OutboxEventRecord event) {
            throw new IllegalStateException("simulated outbox failure");
        }

        @Override public List<OutboxEventRecord> findPendingBatch(int batchSize) { return List.of(); }

        @Override public void markPublished(Collection<UUID> eventIds) { }
    }

    private static final class StubResolutionClaimPort implements ResolutionClaimPort {
        private final int attemptCount;

        private StubResolutionClaimPort(int attemptCount) {
            this.attemptCount = attemptCount;
        }

        @Override
        public ClaimResult tryAcquire(long testRunId) {
            return new ClaimResult.Acquired(UUID.randomUUID(), attemptCount);
        }

        @Override
        public boolean isHeldBy(long testRunId, UUID claimToken) {
            return true;
        }
    }

    private static final class StubExecutionClaimPort implements ExecutionClaimPort {
        @Override
        public ClaimResult tryAcquire(long snapshotId) {
            return new ClaimResult.Acquired(UUID.randomUUID(), 1);
        }

        @Override
        public boolean isHeldBy(long snapshotId, UUID claimToken) {
            return true;
        }
    }
}
