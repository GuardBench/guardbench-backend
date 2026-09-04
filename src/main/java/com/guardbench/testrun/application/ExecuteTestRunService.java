package com.guardbench.testrun.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionPort;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionRequest;
import com.guardbench.testrun.application.port.out.EvaluatorExecutionResult;
import com.guardbench.testrun.application.port.out.EvaluatorFailureCode;
import com.guardbench.testrun.application.port.out.ExecutionClaimPort;
import com.guardbench.testrun.application.port.out.ExecutionContext;
import com.guardbench.testrun.application.port.out.LoadExecutionContextPort;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.TargetExecutionPort;
import com.guardbench.testrun.application.port.out.TargetExecutionRequest;
import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;
import com.guardbench.testrun.application.port.out.TargetProviderException;
import com.guardbench.testrun.application.port.out.TransactionalPhasePort;
import com.guardbench.testrun.domain.ApplicationResponse;
import com.guardbench.testrun.domain.EvaluatorReference;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionError;
import com.guardbench.testrun.domain.TestExecutionErrorCode;
import com.guardbench.testrun.domain.TestExecutionErrorStage;
import com.guardbench.testrun.domain.TestExecutionId;
import com.guardbench.testrun.domain.TargetReference;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;

/**
 * Application Target → Evaluator 실행을 담당하는 TestRun Execution Worker다.
 *
 * <p>외부 호출은 트랜잭션 밖에서 수행하고, claim 재검증 이후 terminal 실행 결과와
 * 완료 Outbox만 persistence phase에서 원자적으로 저장한다. Target이 실패하면 Evaluator를
 * 호출하지 않으며, Evaluator 실패 시 Target의 Application response는 보존하지만 verdict와
 * Assertion은 생성하지 않는다.
 *
 * <p>Provider business retry(PROVIDER_UNAVAILABLE/PROVIDER_TIMEOUT)의 소유권은
 * {@link #MAX_EXECUTION_ATTEMPTS}(claim retry) 한 계층에만 둔다. Target/Evaluator SDK
 * client는 자체 retry를 비활성화({@code max-attempts=1})하도록 설정해야 하며, 두 계층이
 * 동시에 재시도하면 실제 Provider 호출 횟수가 두 값의 곱으로 증폭된다.
 */
public class ExecuteTestRunService {

    private static final Logger log = LoggerFactory.getLogger(ExecuteTestRunService.class);
    private static final int MAX_EXECUTION_ATTEMPTS = 3;

    private final ExecutionClaimPort executionClaimPort;
    private final TestExecutionRepository testExecutionRepository;
    private final LoadExecutionContextPort loadExecutionContextPort;
    private final TargetExecutionPort targetExecutionPort;
    private final EvaluatorExecutionPort evaluatorExecutionPort;
    private final OutboxPort outboxPort;
    private final TransactionalPhasePort transactionalPhasePort;
    private final Clock clock;

    public ExecuteTestRunService(
            ExecutionClaimPort executionClaimPort,
            TestExecutionRepository testExecutionRepository,
            LoadExecutionContextPort loadExecutionContextPort,
            TargetExecutionPort targetExecutionPort,
            EvaluatorExecutionPort evaluatorExecutionPort,
            OutboxPort outboxPort,
            TransactionalPhasePort transactionalPhasePort,
            Clock clock
    ) {
        this.executionClaimPort = Objects.requireNonNull(executionClaimPort);
        this.testExecutionRepository = Objects.requireNonNull(testExecutionRepository);
        this.loadExecutionContextPort = Objects.requireNonNull(loadExecutionContextPort);
        this.targetExecutionPort = Objects.requireNonNull(targetExecutionPort);
        this.evaluatorExecutionPort = Objects.requireNonNull(evaluatorExecutionPort);
        this.outboxPort = Objects.requireNonNull(outboxPort);
        this.transactionalPhasePort = Objects.requireNonNull(transactionalPhasePort);
        this.clock = Objects.requireNonNull(clock);
    }

    public ExecutionOutcome execute(long snapshotId) {
        long executionStartedNanos = System.nanoTime();
        TestExecutionId executionId = new TestExecutionId(new TestCaseSnapshotId(snapshotId));

        if (testExecutionRepository.findById(executionId).isPresent()) {
            log.info("TestExecution이 이미 terminal 상태입니다. snapshotId={} elapsedMs={}",
                    snapshotId, elapsedMs(executionStartedNanos));
            return ExecutionOutcome.ALREADY_TERMINAL;
        }

        ClaimResult claimResult = executionClaimPort.tryAcquire(snapshotId);
        if (claimResult instanceof ClaimResult.AlreadyHeld) {
            log.warn("다른 Worker가 TestExecution claim을 보유 중입니다. snapshotId={} elapsedMs={}",
                    snapshotId, elapsedMs(executionStartedNanos));
            return ExecutionOutcome.CLAIM_HELD_BY_OTHER;
        }

        ClaimResult.Acquired acquired = (ClaimResult.Acquired) claimResult;
        UUID claimToken = acquired.claimToken();
        int attemptCount = acquired.attemptCount();
        ExecutionContext context = loadExecutionContextPort.load(snapshotId).orElse(null);
        if (context == null) {
            log.warn("TestExecution context를 찾을 수 없습니다. snapshotId={} attemptCount={} elapsedMs={}",
                    snapshotId, attemptCount, elapsedMs(executionStartedNanos));
            return ExecutionOutcome.CONTEXT_NOT_FOUND;
        }

        Instant startedAt = clock.instant();
        TargetExecutionNormalization targetNormalization = callTarget(context);
        if (!targetNormalization.isSuccess()) {
            TestExecutionError error = targetNormalization.error();
            boolean retryable = isRetryable(error.code());
            if (retryable && attemptCount < MAX_EXECUTION_ATTEMPTS) {
                log.warn("Application target 실패로 재시도합니다. testRunId={} snapshotId={} attemptCount={} "
                                + "errorStage={} errorCode={} retryable={} reason={} elapsedMs={}",
                        context.testRunId(), snapshotId, attemptCount, error.stage(), error.code(), retryable,
                        error.message(), elapsedMs(executionStartedNanos));
                return ExecutionOutcome.PROVIDER_FAILED_RETRYABLE;
            }
        }

        EvaluatorExecutionNormalization evaluatorNormalization = null;
        if (targetNormalization.isSuccess()) {
            ApplicationResponse response = targetNormalization.applicationResponse();
            evaluatorNormalization = callEvaluator(context, response);
            if (!evaluatorNormalization.isSuccess()) {
                TestExecutionError error = evaluatorNormalization.error();
                boolean retryable = isRetryable(error.code());
                if (retryable && attemptCount < MAX_EXECUTION_ATTEMPTS) {
                    log.warn("Evaluator 실패로 재시도합니다. testRunId={} snapshotId={} attemptCount={} "
                                    + "errorStage={} errorCode={} retryable={} reason={} elapsedMs={}",
                            context.testRunId(), snapshotId, attemptCount, error.stage(), error.code(), retryable,
                            error.message(), elapsedMs(executionStartedNanos));
                    return ExecutionOutcome.PROVIDER_FAILED_RETRYABLE;
                }
            }
        }

        if (!executionClaimPort.isHeldBy(snapshotId, claimToken)) {
            log.warn("Provider 호출 이후 TestExecution claim을 상실했습니다. testRunId={} snapshotId={} attemptCount={} elapsedMs={}",
                    context.testRunId(), snapshotId, attemptCount, elapsedMs(executionStartedNanos));
            return ExecutionOutcome.CLAIM_LOST_AFTER_EXECUTION;
        }

        Instant completedAt = clock.instant();
        TestExecution terminalExecution = buildTerminalExecution(
                executionId, targetNormalization, evaluatorNormalization, startedAt, completedAt);
        OutboxEventRecord completedEvent = completedEvent(context.testRunId(), snapshotId, completedAt);
        transactionalPhasePort.runInTransaction(() -> {
            testExecutionRepository.save(terminalExecution);
            outboxPort.save(completedEvent);
        });

        log.info("TestExecution terminal 결과를 저장했습니다. testRunId={} snapshotId={} attemptCount={} status={} "
                        + "evaluatorVerdict={} errorStage={} errorCode={} retryable={} eventId={} elapsedMs={}",
                context.testRunId(), snapshotId, attemptCount, terminalExecution.status(),
                terminalExecution.evaluationResult() != null ? terminalExecution.evaluationResult().action() : null,
                terminalExecution.error() != null ? terminalExecution.error().stage() : null,
                terminalExecution.error() != null ? terminalExecution.error().code() : null,
                terminalExecution.error() != null && isRetryable(terminalExecution.error().code()),
                completedEvent.eventId(), elapsedMs(executionStartedNanos));
        return ExecutionOutcome.EXECUTED;
    }

    private TargetExecutionNormalization callTarget(ExecutionContext context) {
        try {
            TargetExecutionResult result = targetExecutionPort.execute(new TargetExecutionRequest(
                    new TargetReference(context.targetReference()), context.input()));
            return TargetResultNormalizer.normalize(result);
        } catch (TargetProviderException exception) {
            return TargetResultNormalizer.normalize(TargetExecutionResult.failed(exception.failureCode()));
        } catch (RuntimeException exception) {
            return TargetResultNormalizer.normalize(TargetExecutionResult.failed(TargetFailureCode.PROVIDER_UNAVAILABLE));
        }
    }

    private EvaluatorExecutionNormalization callEvaluator(
            ExecutionContext context,
            ApplicationResponse applicationResponse) {
        if (context.evaluatorReference() == null || context.evaluatorReference().isBlank()) {
            return EvaluatorResultNormalizer.normalize(
                    EvaluatorExecutionResult.failed(EvaluatorFailureCode.EVALUATOR_NOT_FOUND));
        }

        try {
            EvaluatorExecutionResult result = evaluatorExecutionPort.evaluate(new EvaluatorExecutionRequest(
                    new EvaluatorReference(context.evaluatorReference()), context.input(), applicationResponse.value()));
            return EvaluatorResultNormalizer.normalize(result);
        } catch (RuntimeException exception) {
            return EvaluatorResultNormalizer.normalize(
                    EvaluatorExecutionResult.failed(EvaluatorFailureCode.PROVIDER_UNAVAILABLE));
        }
    }

    private static boolean isRetryable(TestExecutionErrorCode errorCode) {
        return switch (errorCode) {
            case PROVIDER_UNAVAILABLE, PROVIDER_TIMEOUT -> true;
            case TARGET_NOT_FOUND, TARGET_ACCESS_DENIED, TARGET_CONFIGURATION_INVALID,
                 EVALUATOR_NOT_FOUND, EVALUATOR_ACCESS_DENIED, EVALUATOR_CONFIGURATION_INVALID,
                 PROVIDER_RESPONSE_INVALID -> false;
        };
    }

    private static TestExecution buildTerminalExecution(
            TestExecutionId executionId,
            TargetExecutionNormalization targetNormalization,
            EvaluatorExecutionNormalization evaluatorNormalization,
            Instant startedAt,
            Instant completedAt
    ) {
        if (targetNormalization.isSuccess() && evaluatorNormalization.isSuccess()) {
            return TestExecution.succeeded(
                    executionId,
                    targetNormalization.applicationResponse(),
                    evaluatorNormalization.evaluationResult(),
                    startedAt,
                    completedAt);
        }

        TestExecutionError error = targetNormalization.isSuccess()
                ? evaluatorNormalization.error()
                : targetNormalization.error();
        if (error.code() == TestExecutionErrorCode.PROVIDER_TIMEOUT) {
            if (targetNormalization.isSuccess()) {
                return TestExecution.timedOutAfterApplication(
                        executionId, targetNormalization.applicationResponse(), error, startedAt, completedAt);
            }
            return TestExecution.timedOut(executionId, error, startedAt, completedAt);
        }
        if (targetNormalization.isSuccess()
                && error.stage() == TestExecutionErrorStage.EVALUATOR) {
            return TestExecution.failedAfterApplication(
                    executionId, targetNormalization.applicationResponse(), error, startedAt, completedAt);
        }
        return TestExecution.failed(executionId, error, startedAt, completedAt);
    }

    private static long elapsedMs(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    private OutboxEventRecord completedEvent(long testRunId, long snapshotId, Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","eventType":"TestExecutionCompleted","schemaVersion":2,"testRunId":%d,"snapshotId":%d,"occurredAt":"%s"}
                """.formatted(eventId, testRunId, snapshotId, occurredAt).strip();
        return OutboxEventRecord.pending(
                eventId, "TestExecutionCompleted", payload,
                "TestExecutionCompleted:" + snapshotId, occurredAt);
    }

    public enum ExecutionOutcome {
        EXECUTED,
        ALREADY_TERMINAL,
        CONTEXT_NOT_FOUND,
        CLAIM_HELD_BY_OTHER,
        CLAIM_LOST_AFTER_EXECUTION,
        PROVIDER_FAILED_RETRYABLE;

        public boolean shouldAcknowledge() {
            return switch (this) {
                case EXECUTED, ALREADY_TERMINAL, CONTEXT_NOT_FOUND -> true;
                case CLAIM_HELD_BY_OTHER, CLAIM_LOST_AFTER_EXECUTION, PROVIDER_FAILED_RETRYABLE -> false;
            };
        }
    }
}
