package com.guardbench.testrun.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.LoadSnapshotIdsByTestRunPort;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.ResolutionClaimPort;
import com.guardbench.testrun.application.port.out.SaveNotEvaluatedQualityGatePort;
import com.guardbench.testrun.application.port.out.TransactionalPhasePort;
import com.guardbench.testrun.application.port.out.TargetPreparationPort;
import com.guardbench.testrun.application.port.out.TargetPreparationRequest;
import com.guardbench.testrun.application.port.out.TargetProviderException;
import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionId;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestRun;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.TestRunStatus;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;
import com.guardbench.testrun.domain.repository.TestRunRepository;

/**
 * Resolution Worker Application Service다.
 *
 * <p>ADR 0010에 따라 {@code TestRunRequested} 메시지를 소비하여 Target을
 * 준비하고, 모든 Snapshot의 단일 {@code TestExecutionRequested} Outbox를 fan-out한다.
 *
 * <p>처리 순서:
 * <ol>
 *   <li>resolution claim 선점</li>
 *   <li>QUEUED → PREPARING 전환 (persistence phase 트랜잭션)</li>
 *   <li>Target 준비 (DB 트랜잭션 밖)</li>
 *   <li>claim 소유 재검증</li>
 *   <li>PREPARING → RUNNING + fan-out Outbox 원자적 저장 (persistence phase 트랜잭션)</li>
 * </ol>
 *
 * <p>materialization 실패 시 모든 TestExecution을 NOT_STARTED로 저장하고
 * TestRun을 FINISHED/ERROR로 종결한다. 이 종결도 하나의 트랜잭션으로 수행한다.
 *
 * <p>외부 Provider 호출은 트랜잭션 밖에서 수행해야 하므로
 * 메서드 전체가 아니라 {@link TransactionalPhasePort}로 phase 단위 경계를 선언한다.
 */
public class ResolveTestRunService {

    private static final Logger log = LoggerFactory.getLogger(ResolveTestRunService.class);
    private static final int MAX_RESOLUTION_ATTEMPTS = 3;

    private final ResolutionClaimPort resolutionClaimPort;
    private final TestRunRepository testRunRepository;
    private final TargetPreparationPort preparationPort;
    private final LoadSnapshotIdsByTestRunPort loadSnapshotIdsPort;
    private final OutboxPort outboxPort;
    private final TestExecutionRepository testExecutionRepository;
    private final SaveNotEvaluatedQualityGatePort saveNotEvaluatedQualityGatePort;
    private final TransactionalPhasePort transactionalPhasePort;
    private final Clock clock;

    public ResolveTestRunService(
            ResolutionClaimPort resolutionClaimPort,
            TestRunRepository testRunRepository,
            TargetPreparationPort preparationPort,
            LoadSnapshotIdsByTestRunPort loadSnapshotIdsPort,
            OutboxPort outboxPort,
            TestExecutionRepository testExecutionRepository,
            SaveNotEvaluatedQualityGatePort saveNotEvaluatedQualityGatePort,
            TransactionalPhasePort transactionalPhasePort,
            Clock clock
    ) {
        this.resolutionClaimPort = Objects.requireNonNull(resolutionClaimPort);
        this.testRunRepository = Objects.requireNonNull(testRunRepository);
        this.preparationPort = Objects.requireNonNull(preparationPort);
        this.loadSnapshotIdsPort = Objects.requireNonNull(loadSnapshotIdsPort);
        this.outboxPort = Objects.requireNonNull(outboxPort);
        this.testExecutionRepository = Objects.requireNonNull(testExecutionRepository);
        this.saveNotEvaluatedQualityGatePort = Objects.requireNonNull(saveNotEvaluatedQualityGatePort);
        this.transactionalPhasePort = Objects.requireNonNull(transactionalPhasePort);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * TestRunRequested 메시지를 처리한다.
     *
     * @return 처리 결과를 나타내는 값
     */
    public ResolutionOutcome resolve(long testRunId) {
        long resolutionStartedNanos = System.nanoTime();
        log.info("TestRun resolution을 시작합니다. testRunId={}", testRunId);
        TestRun testRun = testRunRepository.findById(new TestRunId(testRunId))
                .orElse(null);
        if (testRun == null) {
            log.warn("TestRun을 찾을 수 없어 resolution을 건너뜁니다. testRunId={} elapsedMs={}",
                    testRunId, elapsedMs(resolutionStartedNanos));
            return ResolutionOutcome.NOT_FOUND;
        }

        // 이미 RUNNING 또는 FINISHED면 멱등 성공
        if (testRun.status() == TestRunStatus.RUNNING || testRun.status() == TestRunStatus.FINISHED) {
            log.info("TestRun resolution이 이미 완료되었습니다. testRunId={} status={} elapsedMs={}",
                    testRunId, testRun.status(), elapsedMs(resolutionStartedNanos));
            return ResolutionOutcome.ALREADY_RESOLVED;
        }

        // claim 선점
        ClaimResult claimResult = resolutionClaimPort.tryAcquire(testRunId);
        if (claimResult instanceof ClaimResult.AlreadyHeld) {
            log.warn("다른 Worker가 TestRun resolution claim을 보유 중입니다. testRunId={} elapsedMs={}",
                    testRunId, elapsedMs(resolutionStartedNanos));
            return ResolutionOutcome.CLAIM_HELD_BY_OTHER;
        }

        ClaimResult.Acquired acquired = (ClaimResult.Acquired) claimResult;
        UUID claimToken = acquired.claimToken();
        int attemptCount = acquired.attemptCount();
        log.info("TestRun resolution claim을 획득했습니다. testRunId={} attemptCount={}", testRunId, attemptCount);

        // QUEUED → PREPARING 전환 (persistence phase 트랜잭션)
        if (testRun.status() == TestRunStatus.QUEUED) {
            Instant preparingAt = clock.instant();
            transactionalPhasePort.runInTransaction(() -> {
                testRun.beginPreparing(preparingAt);
                testRunRepository.save(testRun);
            });
        }

        // Materialization (DB 트랜잭션 밖, 외부 호출)
        try {
            long materializationStartedNanos = System.nanoTime();
            log.info("Target 준비를 시작합니다. testRunId={} attemptCount={}", testRunId, attemptCount);
            TargetPreparationRequest request = new TargetPreparationRequest(
                    testRun.targetReference(),
                    testRunId);
            preparationPort.prepare(request);
            log.info("Target 준비를 완료했습니다. testRunId={} attemptCount={} elapsedMs={}",
                    testRunId, attemptCount, elapsedMs(materializationStartedNanos));
        } catch (TargetProviderException exception) {
            log.warn("Target 준비에 실패했습니다. testRunId={} attemptCount={} failureCode={} elapsedMs={}",
                    testRunId, attemptCount, exception.failureCode(), elapsedMs(resolutionStartedNanos));
            return handleMaterializationFailure(testRun, attemptCount);
        }

        // claim 소유 재검증
        if (!resolutionClaimPort.isHeldBy(testRunId, claimToken)) {
            log.warn("Materialization 이후 TestRun resolution claim을 상실했습니다. testRunId={} attemptCount={} elapsedMs={}",
                    testRunId, attemptCount, elapsedMs(resolutionStartedNanos));
            return ResolutionOutcome.CLAIM_LOST_AFTER_MATERIALIZATION;
        }

        // PREPARING → RUNNING + fan-out Outbox 원자적 저장 (persistence phase 트랜잭션)
        Instant now = clock.instant();
        int[] snapshotCount = new int[1];
        transactionalPhasePort.runInTransaction(() -> {
            testRun.beginRunning(now);
            testRunRepository.save(testRun);

            List<Long> snapshotIds = loadSnapshotIdsPort.loadSnapshotIdsByTestRunId(testRunId);
            snapshotCount[0] = snapshotIds.size();
            for (long snapshotId : snapshotIds) {
                outboxPort.save(executionRequestedEvent(testRunId, snapshotId, now));
            }
        });

        log.info("TestRun execution fan-out을 완료했습니다. testRunId={} snapshotCount={} executionEventCount={} elapsedMs={}",
                testRunId, snapshotCount[0], snapshotCount[0], elapsedMs(resolutionStartedNanos));

        return ResolutionOutcome.RESOLVED;
    }

    private ResolutionOutcome handleMaterializationFailure(TestRun testRun, int attemptCount) {
        if (attemptCount < MAX_RESOLUTION_ATTEMPTS) {
            log.warn("Target 준비 실패로 재시도합니다. testRunId={} attemptCount={}",
                    testRun.id().value(), attemptCount);
            return ResolutionOutcome.MATERIALIZATION_FAILED_RETRYABLE;
        }

        // 영구 실패: 모든 execution NOT_STARTED + QualityGateResult NOT_EVALUATED + TestRun FINISHED/ERROR
        long testRunId = testRun.id().value();
        Instant now = clock.instant();

        // ADR 0004: 세 쓰기를 하나의 트랜잭션으로 묶어 원자적으로 종결한다.
        transactionalPhasePort.runInTransaction(() -> {
            List<Long> snapshotIds = loadSnapshotIdsPort.loadSnapshotIdsByTestRunId(testRunId);
            for (long snapshotId : snapshotIds) {
                TestCaseSnapshotId sid = new TestCaseSnapshotId(snapshotId);
                testExecutionRepository.save(
                        TestExecution.notStarted(new TestExecutionId(sid))
                );
            }

            saveNotEvaluatedQualityGatePort.saveNotEvaluated(testRunId);

            testRun.failPreparation(now);
            testRunRepository.save(testRun);
        });

        log.error("Target 준비 영구 실패로 TestRun을 종료했습니다. testRunId={} attemptCount={}",
                testRunId, attemptCount);

        return ResolutionOutcome.MATERIALIZATION_FAILED_TERMINAL;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private OutboxEventRecord executionRequestedEvent(
            long testRunId,
            long snapshotId,
            Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","eventType":"TestExecutionRequested","schemaVersion":2,"testRunId":%d,"snapshotId":%d,"occurredAt":"%s"}
                """.formatted(eventId, testRunId, snapshotId, occurredAt).strip();
        String deduplicationKey = "TestExecutionRequested:" + snapshotId;
        return OutboxEventRecord.pending(eventId, "TestExecutionRequested", payload, deduplicationKey, occurredAt);
    }

    /**
     * Resolution Worker 처리 결과를 나타낸다. SQS ack/nack 판정에 사용한다.
     */
    public enum ResolutionOutcome {
        /** 정상적으로 materialization과 fan-out이 완료되었다. */
        RESOLVED,
        /** TestRun이 이미 RUNNING 또는 FINISHED라 중복 처리가 필요 없다. */
        ALREADY_RESOLVED,
        /** TestRun이 존재하지 않는다. */
        NOT_FOUND,
        /** 다른 Worker가 유효한 claim을 보유하고 있다. */
        CLAIM_HELD_BY_OTHER,
        /** Materialization 후 claim 소유권이 상실되었다. */
        CLAIM_LOST_AFTER_MATERIALIZATION,
        /** Materialization 실패, 재시도 가능 (attempt 한도 미초과). */
        MATERIALIZATION_FAILED_RETRYABLE,
        /** Materialization 최종 실패, TestRun을 ERROR로 종결하였다. */
        MATERIALIZATION_FAILED_TERMINAL
    }
}
