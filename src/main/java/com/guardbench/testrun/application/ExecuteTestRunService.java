package com.guardbench.testrun.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.guardbench.testrun.application.messaging.TargetTypeCode;
import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.ExecutionClaimPort;
import com.guardbench.testrun.application.port.out.ExecutionContext;
import com.guardbench.testrun.application.port.out.GuardrailExecutionPort;
import com.guardbench.testrun.application.port.out.GuardrailExecutionRequest;
import com.guardbench.testrun.application.port.out.GuardrailExecutionResult;
import com.guardbench.testrun.application.port.out.GuardrailProviderException;
import com.guardbench.testrun.application.port.out.LoadExecutionContextPort;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.TransactionalPhasePort;
import com.guardbench.testrun.domain.ActualResult;
import com.guardbench.testrun.domain.TargetType;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionError;
import com.guardbench.testrun.domain.TestExecutionErrorCode;
import com.guardbench.testrun.domain.TestExecutionId;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;

/**
 * Execution Worker Application Service다.
 *
 * <p>ADR 0005에 따라 {@code TestExecutionRequested} 메시지를 소비하여 하나의
 * {@code (snapshotId, targetType)} 단위로 Bedrock Guardrail 실행을 수행하고
 * terminal {@code TestExecution}과 {@code TestExecutionCompleted} Outbox를 저장한다.
 *
 * <p>처리 순서:
 * <ol>
 *   <li>이미 terminal TestExecution이 있으면 멱등 성공(ack)</li>
 *   <li>execution claim 선점</li>
 *   <li>실행 컨텍스트(Snapshot input + guardrail version) 로드</li>
 *   <li>Guardrail Provider 호출 (DB 트랜잭션 밖)</li>
 *   <li>claim 소유 재검증</li>
 *   <li>terminal TestExecution + TestExecutionCompleted Outbox 원자적 저장 (persistence phase 트랜잭션)</li>
 * </ol>
 *
 * <p>Provider 호출은 트랜잭션 밖에서 수행해야 하므로 메서드 전체를 감싸지 않고
 * {@link TransactionalPhasePort}로 마지막 저장 phase만 트랜잭션 경계로 선언한다.
 */
public class ExecuteTestRunService {

    private static final Logger log = LoggerFactory.getLogger(ExecuteTestRunService.class);
    private static final int MAX_EXECUTION_ATTEMPTS = 3;

    private final ExecutionClaimPort executionClaimPort;
    private final TestExecutionRepository testExecutionRepository;
    private final LoadExecutionContextPort loadExecutionContextPort;
    private final GuardrailExecutionPort guardrailExecutionPort;
    private final OutboxPort outboxPort;
    private final TransactionalPhasePort transactionalPhasePort;
    private final Clock clock;

    public ExecuteTestRunService(
            ExecutionClaimPort executionClaimPort,
            TestExecutionRepository testExecutionRepository,
            LoadExecutionContextPort loadExecutionContextPort,
            GuardrailExecutionPort guardrailExecutionPort,
            OutboxPort outboxPort,
            TransactionalPhasePort transactionalPhasePort,
            Clock clock
    ) {
        this.executionClaimPort = Objects.requireNonNull(executionClaimPort);
        this.testExecutionRepository = Objects.requireNonNull(testExecutionRepository);
        this.loadExecutionContextPort = Objects.requireNonNull(loadExecutionContextPort);
        this.guardrailExecutionPort = Objects.requireNonNull(guardrailExecutionPort);
        this.outboxPort = Objects.requireNonNull(outboxPort);
        this.transactionalPhasePort = Objects.requireNonNull(transactionalPhasePort);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * TestExecutionRequested 메시지를 처리한다.
     *
     * @return SQS ack/nack 판정에 사용하는 처리 결과
     */
    public ExecutionOutcome execute(long snapshotId, TargetTypeCode targetTypeCode) {
        Objects.requireNonNull(targetTypeCode, "targetTypeCode must not be null");
        long executionStartedNanos = System.nanoTime();

        String targetType = targetTypeCode.name();
        TestExecutionId executionId = toExecutionId(snapshotId, targetTypeCode);

        // 이미 terminal 결과가 있으면 멱등 ack
        if (testExecutionRepository.findById(executionId).isPresent()) {
            log.info("TestExecution already terminal. snapshotId={} targetType={} elapsedMs={}",
                    snapshotId, targetType, elapsedMs(executionStartedNanos));
            return ExecutionOutcome.ALREADY_TERMINAL;
        }

        // claim 선점
        ClaimResult claimResult = executionClaimPort.tryAcquire(snapshotId, targetType);
        if (claimResult instanceof ClaimResult.AlreadyHeld) {
            log.warn("TestExecution claim held by another worker. snapshotId={} targetType={} elapsedMs={}",
                    snapshotId, targetType, elapsedMs(executionStartedNanos));
            return ExecutionOutcome.CLAIM_HELD_BY_OTHER;
        }

        ClaimResult.Acquired acquired = (ClaimResult.Acquired) claimResult;
        UUID claimToken = acquired.claimToken();
        int attemptCount = acquired.attemptCount();
        log.info("TestExecution claim acquired. snapshotId={} targetType={} attemptCount={}",
                snapshotId, targetType, attemptCount);

        // 실행 컨텍스트 로드
        ExecutionContext context = loadExecutionContextPort.load(snapshotId, targetType)
                .orElse(null);
        if (context == null) {
            log.warn("TestExecution context not found. snapshotId={} targetType={} attemptCount={} elapsedMs={}",
                    snapshotId, targetType, attemptCount, elapsedMs(executionStartedNanos));
            return ExecutionOutcome.CONTEXT_NOT_FOUND;
        }

        // Provider 호출 (DB 트랜잭션 밖)
        Instant startedAt = clock.instant();
        long providerStartedNanos = System.nanoTime();
        log.info("Bedrock ApplyGuardrail started. testRunId={} snapshotId={} targetType={} attemptCount={}",
                context.testRunId(), snapshotId, targetType, attemptCount);
        GuardrailExecutionNormalization normalization = callProvider(context);
        Instant completedAt = clock.instant();
        log.info("Bedrock ApplyGuardrail completed. testRunId={} snapshotId={} targetType={} attemptCount={} succeeded={} elapsedMs={}",
                context.testRunId(), snapshotId, targetType, attemptCount, normalization.isSuccess(),
                elapsedMs(providerStartedNanos));

        // Provider 실패 시 재시도 가능 여부 판단
        if (!normalization.isSuccess()) {
            TestExecutionError error = normalization.error();
            if (isRetryable(error.code()) && attemptCount < MAX_EXECUTION_ATTEMPTS) {
                log.warn("TestExecution provider failure will be retried. testRunId={} snapshotId={} targetType={} attemptCount={} failureCode={} elapsedMs={}",
                        context.testRunId(), snapshotId, targetType, attemptCount, error.code(),
                        elapsedMs(executionStartedNanos));
                return ExecutionOutcome.PROVIDER_FAILED_RETRYABLE;
            }
            // 영구 실패 또는 재시도 소진: terminal 결과 저장으로 진행
        }

        // claim 소유 재검증
        if (!executionClaimPort.isHeldBy(snapshotId, targetType, claimToken)) {
            log.warn("TestExecution claim lost after provider call. testRunId={} snapshotId={} targetType={} attemptCount={} elapsedMs={}",
                    context.testRunId(), snapshotId, targetType, attemptCount, elapsedMs(executionStartedNanos));
            return ExecutionOutcome.CLAIM_LOST_AFTER_EXECUTION;
        }

        // Terminal TestExecution + Outbox 원자 저장 (persistence phase 트랜잭션)
        TestExecution terminalExecution = buildTerminalExecution(
                executionId, normalization, startedAt, completedAt
        );
        OutboxEventRecord completedEvent = completedEvent(context.testRunId(), snapshotId, targetTypeCode, completedAt);
        transactionalPhasePort.runInTransaction(() -> {
            testExecutionRepository.save(terminalExecution);
            outboxPort.save(completedEvent);
        });

        log.info("TestExecution terminal result saved. testRunId={} snapshotId={} targetType={} attemptCount={} status={} eventId={} eventType={} elapsedMs={}",
                context.testRunId(), snapshotId, targetType, attemptCount, terminalExecution.status(),
                completedEvent.eventId(), completedEvent.eventType(), elapsedMs(executionStartedNanos));

        return ExecutionOutcome.EXECUTED;
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private GuardrailExecutionNormalization callProvider(ExecutionContext context) {
        try {
            GuardrailExecutionRequest request = new GuardrailExecutionRequest(
                    context.guardrailIdentifier(),
                    context.guardrailVersion(),
                    context.input()
            );
            GuardrailExecutionResult result = guardrailExecutionPort.execute(request);
            return GuardrailResultNormalizer.normalize(result);
        } catch (GuardrailProviderException exception) {
            GuardrailExecutionResult failedResult = GuardrailExecutionResult.failed(exception.failureCode());
            return GuardrailResultNormalizer.normalize(failedResult);
        }
    }

    private static boolean isRetryable(TestExecutionErrorCode errorCode) {
        return switch (errorCode) {
            case PROVIDER_UNAVAILABLE, PROVIDER_TIMEOUT -> true;
            case TARGET_NOT_FOUND, TARGET_ACCESS_DENIED,
                 TARGET_CONFIGURATION_INVALID, PROVIDER_RESPONSE_INVALID -> false;
        };
    }

    private static TestExecution buildTerminalExecution(
            TestExecutionId executionId,
            GuardrailExecutionNormalization normalization,
            Instant startedAt,
            Instant completedAt
    ) {
        if (normalization.isSuccess()) {
            return TestExecution.succeeded(executionId, normalization.actualResult(), startedAt, completedAt);
        }

        TestExecutionError error = normalization.error();
        if (error.code() == TestExecutionErrorCode.PROVIDER_TIMEOUT) {
            return TestExecution.timedOut(executionId, error, startedAt, completedAt);
        }
        return TestExecution.failed(executionId, error, startedAt, completedAt);
    }

    private OutboxEventRecord completedEvent(
            long testRunId,
            long snapshotId,
            TargetTypeCode targetType,
            Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","eventType":"TestExecutionCompleted","schemaVersion":1,"testRunId":%d,"snapshotId":%d,"targetType":"%s","occurredAt":"%s"}
                """.formatted(eventId, testRunId, snapshotId, targetType.name(), occurredAt).strip();
        String deduplicationKey = "TestExecutionCompleted:" + snapshotId + ":" + targetType.name();
        return OutboxEventRecord.pending(eventId, "TestExecutionCompleted", payload, deduplicationKey, occurredAt);
    }

    private static TestExecutionId toExecutionId(long snapshotId, TargetTypeCode targetTypeCode) {
        TargetType targetType = switch (targetTypeCode) {
            case BASELINE -> TargetType.BASELINE;
            case CANDIDATE -> TargetType.CANDIDATE;
        };
        return new TestExecutionId(new TestCaseSnapshotId(snapshotId), targetType);
    }

    /**
     * Execution Worker 처리 결과를 나타낸다. SQS ack/nack 판정에 사용한다.
     */
    public enum ExecutionOutcome {
        /** 정상적으로 Provider 실행과 terminal 저장이 완료되었다. ack한다. */
        EXECUTED,
        /** 이미 terminal TestExecution이 존재한다. 멱등 ack한다. */
        ALREADY_TERMINAL,
        /** Snapshot 또는 TestRun 컨텍스트가 없다. ack한다. */
        CONTEXT_NOT_FOUND,
        /** 다른 Worker가 유효한 claim을 보유하고 있다. nack한다. */
        CLAIM_HELD_BY_OTHER,
        /** Provider 호출 후 claim 소유권이 상실되었다. nack한다. */
        CLAIM_LOST_AFTER_EXECUTION,
        /** Provider 일시 실패, 재시도 가능 (attempt 한도 미초과). nack한다. */
        PROVIDER_FAILED_RETRYABLE,
        ;

        /**
         * SQS 원본 메시지를 삭제해야 하는지 반환한다.
         * ack 결과는 삭제, nack 결과는 visibility timeout 후 재전달된다.
         */
        public boolean shouldAcknowledge() {
            return switch (this) {
                case EXECUTED, ALREADY_TERMINAL, CONTEXT_NOT_FOUND -> true;
                case CLAIM_HELD_BY_OTHER, CLAIM_LOST_AFTER_EXECUTION, PROVIDER_FAILED_RETRYABLE -> false;
            };
        }
    }
}
