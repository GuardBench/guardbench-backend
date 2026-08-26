package com.guardbench.testrun.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.guardbench.testrun.application.messaging.TargetTypeCode;
import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.GuardrailMaterializationPort;
import com.guardbench.testrun.application.port.out.GuardrailMaterializationRequest;
import com.guardbench.testrun.application.port.out.GuardrailMaterializedVersion;
import com.guardbench.testrun.application.port.out.GuardrailProviderException;
import com.guardbench.testrun.application.port.out.LoadSnapshotIdsByTestRunPort;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.ResolutionClaimPort;
import com.guardbench.testrun.application.port.out.SaveNotEvaluatedQualityGatePort;
import com.guardbench.testrun.application.port.out.TransactionalPhasePort;
import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionId;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TargetType;
import com.guardbench.testrun.domain.TestRun;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.TestRunStatus;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;
import com.guardbench.testrun.domain.repository.TestRunRepository;

/**
 * Resolution Worker Application Service다.
 *
 * <p>ADR 0005에 따라 {@code TestRunRequested} 메시지를 소비하여 Candidate DRAFT를
 * materialization하고, 모든 Snapshot의 Baseline/Candidate {@code TestExecutionRequested}
 * Outbox를 fan-out한다.
 *
 * <p>처리 순서:
 * <ol>
 *   <li>resolution claim 선점</li>
 *   <li>QUEUED → PREPARING 전환 (persistence phase 트랜잭션)</li>
 *   <li>Candidate DRAFT materialization (DB 트랜잭션 밖)</li>
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

    private static final int MAX_RESOLUTION_ATTEMPTS = 3;

    private final ResolutionClaimPort resolutionClaimPort;
    private final TestRunRepository testRunRepository;
    private final GuardrailMaterializationPort materializationPort;
    private final LoadSnapshotIdsByTestRunPort loadSnapshotIdsPort;
    private final OutboxPort outboxPort;
    private final TestExecutionRepository testExecutionRepository;
    private final SaveNotEvaluatedQualityGatePort saveNotEvaluatedQualityGatePort;
    private final TransactionalPhasePort transactionalPhasePort;
    private final Clock clock;

    public ResolveTestRunService(
            ResolutionClaimPort resolutionClaimPort,
            TestRunRepository testRunRepository,
            GuardrailMaterializationPort materializationPort,
            LoadSnapshotIdsByTestRunPort loadSnapshotIdsPort,
            OutboxPort outboxPort,
            TestExecutionRepository testExecutionRepository,
            SaveNotEvaluatedQualityGatePort saveNotEvaluatedQualityGatePort,
            TransactionalPhasePort transactionalPhasePort,
            Clock clock
    ) {
        this.resolutionClaimPort = Objects.requireNonNull(resolutionClaimPort);
        this.testRunRepository = Objects.requireNonNull(testRunRepository);
        this.materializationPort = Objects.requireNonNull(materializationPort);
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
        TestRun testRun = testRunRepository.findById(new TestRunId(testRunId))
                .orElse(null);
        if (testRun == null) {
            return ResolutionOutcome.NOT_FOUND;
        }

        // 이미 RUNNING 또는 FINISHED면 멱등 성공
        if (testRun.status() == TestRunStatus.RUNNING || testRun.status() == TestRunStatus.FINISHED) {
            return ResolutionOutcome.ALREADY_RESOLVED;
        }

        // claim 선점
        ClaimResult claimResult = resolutionClaimPort.tryAcquire(testRunId);
        if (claimResult instanceof ClaimResult.AlreadyHeld) {
            return ResolutionOutcome.CLAIM_HELD_BY_OTHER;
        }

        ClaimResult.Acquired acquired = (ClaimResult.Acquired) claimResult;
        UUID claimToken = acquired.claimToken();
        int attemptCount = acquired.attemptCount();

        // QUEUED → PREPARING 전환 (persistence phase 트랜잭션)
        if (testRun.status() == TestRunStatus.QUEUED) {
            Instant preparingAt = clock.instant();
            transactionalPhasePort.runInTransaction(() -> {
                testRun.beginPreparing(preparingAt);
                testRunRepository.save(testRun);
            });
        }

        // Materialization (DB 트랜잭션 밖, 외부 호출)
        GuardrailMaterializedVersion materializedVersion;
        try {
            GuardrailMaterializationRequest request = new GuardrailMaterializationRequest(
                    testRun.candidateTarget().guardrailId(),
                    testRunId
            );
            materializedVersion = materializationPort.materialize(request);
        } catch (GuardrailProviderException exception) {
            return handleMaterializationFailure(testRun, attemptCount);
        }

        // claim 소유 재검증
        if (!resolutionClaimPort.isHeldBy(testRunId, claimToken)) {
            return ResolutionOutcome.CLAIM_LOST_AFTER_MATERIALIZATION;
        }

        // PREPARING → RUNNING + fan-out Outbox 원자적 저장 (persistence phase 트랜잭션)
        Instant now = clock.instant();
        transactionalPhasePort.runInTransaction(() -> {
            testRun.beginRunning(materializedVersion.version(), now);
            testRunRepository.save(testRun);

            List<Long> snapshotIds = loadSnapshotIdsPort.loadSnapshotIdsByTestRunId(testRunId);
            for (long snapshotId : snapshotIds) {
                outboxPort.save(executionRequestedEvent(testRunId, snapshotId, TargetTypeCode.BASELINE, now));
                outboxPort.save(executionRequestedEvent(testRunId, snapshotId, TargetTypeCode.CANDIDATE, now));
            }
        });

        return ResolutionOutcome.RESOLVED;
    }

    private ResolutionOutcome handleMaterializationFailure(TestRun testRun, int attemptCount) {
        if (attemptCount < MAX_RESOLUTION_ATTEMPTS) {
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
                        TestExecution.notStarted(new TestExecutionId(sid, TargetType.BASELINE))
                );
                testExecutionRepository.save(
                        TestExecution.notStarted(new TestExecutionId(sid, TargetType.CANDIDATE))
                );
            }

            saveNotEvaluatedQualityGatePort.saveNotEvaluated(testRunId);

            testRun.failPreparation(now);
            testRunRepository.save(testRun);
        });

        return ResolutionOutcome.MATERIALIZATION_FAILED_TERMINAL;
    }

    private OutboxEventRecord executionRequestedEvent(
            long testRunId,
            long snapshotId,
            TargetTypeCode targetType,
            Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","eventType":"TestExecutionRequested","schemaVersion":1,"testRunId":%d,"snapshotId":%d,"targetType":"%s","occurredAt":"%s"}
                """.formatted(eventId, testRunId, snapshotId, targetType.name(), occurredAt).strip();
        String deduplicationKey = "TestExecutionRequested:" + snapshotId + ":" + targetType.name();
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
