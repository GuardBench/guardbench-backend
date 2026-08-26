package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.guardbench.testrun.application.messaging.TargetTypeCode;
import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.ExecutionClaimPort;
import com.guardbench.testrun.application.port.out.ExecutionContext;
import com.guardbench.testrun.application.port.out.GuardrailExecutionPort;
import com.guardbench.testrun.application.port.out.GuardrailExecutionRequest;
import com.guardbench.testrun.application.port.out.GuardrailExecutionResult;
import com.guardbench.testrun.application.port.out.GuardrailFailureCode;
import com.guardbench.testrun.application.port.out.GuardrailProviderException;
import com.guardbench.testrun.application.port.out.LoadExecutionContextPort;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.domain.ActualResult;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.TargetType;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionErrorCode;
import com.guardbench.testrun.domain.TestExecutionId;
import com.guardbench.testrun.domain.TestExecutionStatus;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;

class ExecuteTestRunServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final long SNAPSHOT_ID = 100L;
    private static final long TEST_RUN_ID = 1L;
    private static final String GUARDRAIL_ID = "my-guardrail";
    private static final String GUARDRAIL_VERSION = "5";
    private static final String INPUT_TEXT = "Hello, block this content";

    private FakeExecutionClaimPort claimPort;
    private FakeTestExecutionRepository executionRepository;
    private FakeLoadExecutionContextPort contextPort;
    private FakeGuardrailExecutionPort guardrailPort;
    private FakeOutboxPort outboxPort;
    private ExecuteTestRunService service;

    @BeforeEach
    void setUp() {
        claimPort = new FakeExecutionClaimPort();
        executionRepository = new FakeTestExecutionRepository();
        contextPort = new FakeLoadExecutionContextPort();
        guardrailPort = new FakeGuardrailExecutionPort();
        outboxPort = new FakeOutboxPort();
        service = new ExecuteTestRunService(
                claimPort, executionRepository, contextPort,
                guardrailPort, outboxPort, FIXED_CLOCK
        );
    }

    @Nested
    @DisplayName("정상 실행 흐름")
    class HappyPath {

        @Test
        @DisplayName("ALLOW 결과의 Baseline 실행을 SUCCEEDED로 저장하고 Outbox를 생성한다")
        void executesBaselineAllow() {
            claimPort.willAcquire(SNAPSHOT_ID, "BASELINE");
            contextPort.setContext(SNAPSHOT_ID, "BASELINE", defaultContext());
            guardrailPort.willReturn(GuardrailExecutionResult.succeeded("ALLOW"));

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.BASELINE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);
            assertTrue(outcome.shouldAcknowledge());

            // terminal TestExecution이 저장되었다
            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.SUCCEEDED, saved.status());
            assertEquals(new ActualResult(Action.ALLOW), saved.actualResult());
            assertEquals(FIXED_NOW, saved.startedAt());
            assertEquals(FIXED_NOW, saved.completedAt());

            // TestExecutionCompleted Outbox가 저장되었다
            assertEquals(1, outboxPort.savedEvents().size());
            OutboxEventRecord event = outboxPort.savedEvents().getFirst();
            assertEquals("TestExecutionCompleted", event.eventType());
            assertTrue(event.payload().contains("\"snapshotId\":" + SNAPSHOT_ID));
            assertTrue(event.payload().contains("\"targetType\":\"BASELINE\""));
            assertTrue(event.payload().contains("\"testRunId\":" + TEST_RUN_ID));
        }

        @Test
        @DisplayName("BLOCK 결과의 Candidate 실행을 SUCCEEDED로 저장한다")
        void executesCandidateBlock() {
            claimPort.willAcquire(SNAPSHOT_ID, "CANDIDATE");
            contextPort.setContext(SNAPSHOT_ID, "CANDIDATE", defaultContext());
            guardrailPort.willReturn(GuardrailExecutionResult.succeeded("BLOCK"));

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.CANDIDATE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.SUCCEEDED, saved.status());
            assertEquals(new ActualResult(Action.BLOCK), saved.actualResult());
            assertEquals(TargetType.CANDIDATE, saved.id().targetType());
        }

        @Test
        @DisplayName("Provider 호출에 올바른 guardrail identifier, version, input을 전달한다")
        void passesCorrectRequestToProvider() {
            claimPort.willAcquire(SNAPSHOT_ID, "BASELINE");
            contextPort.setContext(SNAPSHOT_ID, "BASELINE", defaultContext());
            guardrailPort.willReturn(GuardrailExecutionResult.succeeded("ALLOW"));

            service.execute(SNAPSHOT_ID, TargetTypeCode.BASELINE);

            GuardrailExecutionRequest request = guardrailPort.lastRequest();
            assertNotNull(request);
            assertEquals(GUARDRAIL_ID, request.guardrailIdentifier());
            assertEquals(GUARDRAIL_VERSION, request.guardrailVersion());
            assertEquals(INPUT_TEXT, request.input());
        }
    }

    @Nested
    @DisplayName("멱등성과 중복 처리")
    class Idempotency {

        @Test
        @DisplayName("이미 terminal TestExecution이 있으면 ALREADY_TERMINAL을 반환한다")
        void alreadyTerminal() {
            TestExecutionId id = new TestExecutionId(
                    new TestCaseSnapshotId(SNAPSHOT_ID), TargetType.BASELINE
            );
            executionRepository.store(
                    TestExecution.succeeded(id, new ActualResult(Action.ALLOW), FIXED_NOW, FIXED_NOW)
            );

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.BASELINE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.ALREADY_TERMINAL, outcome);
            assertTrue(outcome.shouldAcknowledge());
            // Provider가 호출되지 않았다
            assertTrue(guardrailPort.callCount() == 0);
            // Outbox가 추가되지 않았다
            assertTrue(outboxPort.savedEvents().isEmpty());
        }

        @Test
        @DisplayName("FAILED terminal이 존재해도 ALREADY_TERMINAL을 반환한다")
        void alreadyTerminalFailed() {
            TestExecutionId id = new TestExecutionId(
                    new TestCaseSnapshotId(SNAPSHOT_ID), TargetType.CANDIDATE
            );
            executionRepository.store(
                    TestExecution.failed(
                            id,
                            new com.guardbench.testrun.domain.TestExecutionError(
                                    TestExecutionErrorCode.TARGET_NOT_FOUND,
                                    "Guardrail target was not found."
                            ),
                            FIXED_NOW.minusSeconds(10),
                            FIXED_NOW
                    )
            );

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.CANDIDATE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.ALREADY_TERMINAL, outcome);
            assertTrue(outcome.shouldAcknowledge());
        }
    }

    @Nested
    @DisplayName("Claim 경합")
    class ClaimContention {

        @Test
        @DisplayName("다른 Worker가 유효한 claim을 보유하면 CLAIM_HELD_BY_OTHER를 반환한다")
        void claimHeldByOther() {
            claimPort.willBeHeld(SNAPSHOT_ID, "BASELINE");

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.BASELINE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.CLAIM_HELD_BY_OTHER, outcome);
            assertTrue(!outcome.shouldAcknowledge());
            assertEquals(0, guardrailPort.callCount());
        }

        @Test
        @DisplayName("Provider 호출 후 claim을 잃으면 CLAIM_LOST_AFTER_EXECUTION을 반환한다")
        void claimLostAfterExecution() {
            claimPort.willAcquire(SNAPSHOT_ID, "CANDIDATE");
            claimPort.setIsHeldByResult(false); // 재검증 실패
            contextPort.setContext(SNAPSHOT_ID, "CANDIDATE", defaultContext());
            guardrailPort.willReturn(GuardrailExecutionResult.succeeded("ALLOW"));

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.CANDIDATE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.CLAIM_LOST_AFTER_EXECUTION, outcome);
            assertTrue(!outcome.shouldAcknowledge());
            // 결과가 저장되지 않았다
            assertTrue(executionRepository.savedExecutions().isEmpty());
            assertTrue(outboxPort.savedEvents().isEmpty());
        }
    }

    @Nested
    @DisplayName("Provider 실패 처리")
    class ProviderFailure {

        @Test
        @DisplayName("retryable 오류 + attempt 미초과 시 PROVIDER_FAILED_RETRYABLE을 반환한다")
        void retryableUnavailable() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, "BASELINE", 1);
            contextPort.setContext(SNAPSHOT_ID, "BASELINE", defaultContext());
            guardrailPort.willThrow(GuardrailFailureCode.PROVIDER_UNAVAILABLE);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.BASELINE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.PROVIDER_FAILED_RETRYABLE, outcome);
            assertTrue(!outcome.shouldAcknowledge());
            // terminal 결과가 저장되지 않았다
            assertTrue(executionRepository.savedExecutions().isEmpty());
        }

        @Test
        @DisplayName("retryable TIMEOUT + attempt 미초과 시 PROVIDER_FAILED_RETRYABLE을 반환한다")
        void retryableTimeout() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, "BASELINE", 2);
            contextPort.setContext(SNAPSHOT_ID, "BASELINE", defaultContext());
            guardrailPort.willThrow(GuardrailFailureCode.PROVIDER_TIMEOUT);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.BASELINE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.PROVIDER_FAILED_RETRYABLE, outcome);
        }

        @Test
        @DisplayName("retryable 오류 + attempt 소진 시 TIMED_OUT으로 저장한다 (PROVIDER_TIMEOUT)")
        void exhaustedTimeout() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, "BASELINE", 3);
            contextPort.setContext(SNAPSHOT_ID, "BASELINE", defaultContext());
            guardrailPort.willThrow(GuardrailFailureCode.PROVIDER_TIMEOUT);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.BASELINE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);
            assertTrue(outcome.shouldAcknowledge());

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.TIMED_OUT, saved.status());
            assertEquals(TestExecutionErrorCode.PROVIDER_TIMEOUT, saved.error().code());
            assertEquals(1, outboxPort.savedEvents().size());
        }

        @Test
        @DisplayName("retryable 오류(PROVIDER_UNAVAILABLE) + attempt 소진 시 FAILED로 저장한다")
        void exhaustedUnavailable() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, "BASELINE", 3);
            contextPort.setContext(SNAPSHOT_ID, "BASELINE", defaultContext());
            guardrailPort.willThrow(GuardrailFailureCode.PROVIDER_UNAVAILABLE);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.BASELINE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.FAILED, saved.status());
            assertEquals(TestExecutionErrorCode.PROVIDER_UNAVAILABLE, saved.error().code());
        }

        @Test
        @DisplayName("영구 오류(TARGET_NOT_FOUND)는 첫 시도에서 FAILED로 저장한다")
        void permanentFailureFirstAttempt() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, "CANDIDATE", 1);
            contextPort.setContext(SNAPSHOT_ID, "CANDIDATE", defaultContext());
            guardrailPort.willThrow(GuardrailFailureCode.TARGET_NOT_FOUND);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.CANDIDATE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);
            assertTrue(outcome.shouldAcknowledge());

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.FAILED, saved.status());
            assertEquals(TestExecutionErrorCode.TARGET_NOT_FOUND, saved.error().code());
        }

        @Test
        @DisplayName("TARGET_ACCESS_DENIED는 영구 실패로 첫 시도에서 저장한다")
        void permanentAccessDenied() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, "BASELINE", 1);
            contextPort.setContext(SNAPSHOT_ID, "BASELINE", defaultContext());
            guardrailPort.willThrow(GuardrailFailureCode.TARGET_ACCESS_DENIED);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.BASELINE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.FAILED, saved.status());
            assertEquals(TestExecutionErrorCode.TARGET_ACCESS_DENIED, saved.error().code());
        }

        @Test
        @DisplayName("TARGET_CONFIGURATION_INVALID는 영구 실패로 저장한다")
        void permanentConfigInvalid() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, "BASELINE", 2);
            contextPort.setContext(SNAPSHOT_ID, "BASELINE", defaultContext());
            guardrailPort.willThrow(GuardrailFailureCode.TARGET_CONFIGURATION_INVALID);

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.BASELINE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.FAILED, saved.status());
            assertEquals(TestExecutionErrorCode.TARGET_CONFIGURATION_INVALID, saved.error().code());
        }

        @Test
        @DisplayName("PROVIDER_RESPONSE_INVALID는 영구 실패로 저장한다")
        void permanentResponseInvalid() {
            claimPort.willAcquireWithAttempt(SNAPSHOT_ID, "BASELINE", 1);
            contextPort.setContext(SNAPSHOT_ID, "BASELINE", defaultContext());
            guardrailPort.willReturn(GuardrailExecutionResult.failed(GuardrailFailureCode.PROVIDER_RESPONSE_INVALID));

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.BASELINE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.EXECUTED, outcome);

            TestExecution saved = executionRepository.savedExecutions().getFirst();
            assertEquals(TestExecutionStatus.FAILED, saved.status());
            assertEquals(TestExecutionErrorCode.PROVIDER_RESPONSE_INVALID, saved.error().code());
        }
    }

    @Nested
    @DisplayName("컨텍스트 조회 실패")
    class ContextNotFound {

        @Test
        @DisplayName("Snapshot이 없으면 CONTEXT_NOT_FOUND를 반환한다")
        void snapshotNotFound() {
            claimPort.willAcquire(SNAPSHOT_ID, "BASELINE");
            // contextPort에 아무것도 설정하지 않음

            ExecuteTestRunService.ExecutionOutcome outcome =
                    service.execute(SNAPSHOT_ID, TargetTypeCode.BASELINE);

            assertEquals(ExecuteTestRunService.ExecutionOutcome.CONTEXT_NOT_FOUND, outcome);
            assertTrue(outcome.shouldAcknowledge());
            assertEquals(0, guardrailPort.callCount());
        }
    }

    @Nested
    @DisplayName("Deduplication key 계약")
    class DeduplicationKeys {

        @Test
        @DisplayName("Outbox dedup key는 TestExecutionCompleted:snapshotId:targetType 형식이다")
        void deduplicationKeyFormat() {
            claimPort.willAcquire(SNAPSHOT_ID, "CANDIDATE");
            contextPort.setContext(SNAPSHOT_ID, "CANDIDATE", defaultContext());
            guardrailPort.willReturn(GuardrailExecutionResult.succeeded("BLOCK"));

            service.execute(SNAPSHOT_ID, TargetTypeCode.CANDIDATE);

            OutboxEventRecord event = outboxPort.savedEvents().getFirst();
            assertEquals("TestExecutionCompleted:" + SNAPSHOT_ID + ":CANDIDATE", event.deduplicationKey());
        }
    }

    @Nested
    @DisplayName("Ack/Nack 결과")
    class AckNack {

        @Test
        @DisplayName("EXECUTED는 ack 결과다")
        void executedIsAck() {
            assertTrue(ExecuteTestRunService.ExecutionOutcome.EXECUTED.shouldAcknowledge());
        }

        @Test
        @DisplayName("ALREADY_TERMINAL은 ack 결과다")
        void alreadyTerminalIsAck() {
            assertTrue(ExecuteTestRunService.ExecutionOutcome.ALREADY_TERMINAL.shouldAcknowledge());
        }

        @Test
        @DisplayName("CONTEXT_NOT_FOUND는 ack 결과다")
        void contextNotFoundIsAck() {
            assertTrue(ExecuteTestRunService.ExecutionOutcome.CONTEXT_NOT_FOUND.shouldAcknowledge());
        }

        @Test
        @DisplayName("CLAIM_HELD_BY_OTHER는 nack 결과다")
        void claimHeldIsNack() {
            assertTrue(!ExecuteTestRunService.ExecutionOutcome.CLAIM_HELD_BY_OTHER.shouldAcknowledge());
        }

        @Test
        @DisplayName("CLAIM_LOST_AFTER_EXECUTION은 nack 결과다")
        void claimLostIsNack() {
            assertTrue(!ExecuteTestRunService.ExecutionOutcome.CLAIM_LOST_AFTER_EXECUTION.shouldAcknowledge());
        }

        @Test
        @DisplayName("PROVIDER_FAILED_RETRYABLE은 nack 결과다")
        void providerRetryableIsNack() {
            assertTrue(!ExecuteTestRunService.ExecutionOutcome.PROVIDER_FAILED_RETRYABLE.shouldAcknowledge());
        }
    }

    // ─── Test Fixtures ────────────────────────────────────────────────────────

    private static ExecutionContext defaultContext() {
        return new ExecutionContext(GUARDRAIL_ID, GUARDRAIL_VERSION, INPUT_TEXT, TEST_RUN_ID);
    }

    // ─── Fake Adapters ────────────────────────────────────────────────────────

    private static final class FakeExecutionClaimPort implements ExecutionClaimPort {
        private final Map<String, ClaimResult> acquireResults = new HashMap<>();
        private boolean isHeldByResult = true;

        void willAcquire(long snapshotId, String targetType) {
            willAcquireWithAttempt(snapshotId, targetType, 1);
        }

        void willAcquireWithAttempt(long snapshotId, String targetType, int attemptCount) {
            UUID token = UUID.randomUUID();
            acquireResults.put(key(snapshotId, targetType), new ClaimResult.Acquired(token, attemptCount));
        }

        void willBeHeld(long snapshotId, String targetType) {
            acquireResults.put(key(snapshotId, targetType), new ClaimResult.AlreadyHeld());
        }

        void setIsHeldByResult(boolean result) {
            this.isHeldByResult = result;
        }

        @Override
        public ClaimResult tryAcquire(long snapshotId, String targetType) {
            return acquireResults.getOrDefault(key(snapshotId, targetType), new ClaimResult.AlreadyHeld());
        }

        @Override
        public boolean isHeldBy(long snapshotId, String targetType, UUID claimToken) {
            return isHeldByResult;
        }

        private static String key(long snapshotId, String targetType) {
            return snapshotId + ":" + targetType;
        }
    }

    private static final class FakeTestExecutionRepository implements TestExecutionRepository {
        private final Map<TestExecutionId, TestExecution> preLoaded = new HashMap<>();
        private final List<TestExecution> saved = new ArrayList<>();

        void store(TestExecution execution) {
            preLoaded.put(execution.id(), execution);
        }

        List<TestExecution> savedExecutions() {
            return saved;
        }

        @Override
        public Optional<TestExecution> findById(TestExecutionId id) {
            if (preLoaded.containsKey(id)) {
                return Optional.of(preLoaded.get(id));
            }
            return saved.stream()
                    .filter(e -> e.id().equals(id))
                    .findFirst();
        }

        @Override
        public void save(TestExecution execution) {
            saved.add(execution);
        }
    }

    private static final class FakeLoadExecutionContextPort implements LoadExecutionContextPort {
        private final Map<String, ExecutionContext> contexts = new HashMap<>();

        void setContext(long snapshotId, String targetType, ExecutionContext context) {
            contexts.put(snapshotId + ":" + targetType, context);
        }

        @Override
        public Optional<ExecutionContext> load(long snapshotId, String targetType) {
            return Optional.ofNullable(contexts.get(snapshotId + ":" + targetType));
        }
    }

    private static final class FakeGuardrailExecutionPort implements GuardrailExecutionPort {
        private GuardrailExecutionResult successResult;
        private GuardrailFailureCode throwFailureCode;
        private GuardrailExecutionRequest lastRequest;
        private int callCount;

        void willReturn(GuardrailExecutionResult result) {
            this.successResult = result;
            this.throwFailureCode = null;
        }

        void willThrow(GuardrailFailureCode failureCode) {
            this.throwFailureCode = failureCode;
            this.successResult = null;
        }

        GuardrailExecutionRequest lastRequest() {
            return lastRequest;
        }

        int callCount() {
            return callCount;
        }

        @Override
        public GuardrailExecutionResult execute(GuardrailExecutionRequest request) {
            this.lastRequest = request;
            this.callCount++;
            if (throwFailureCode != null) {
                throw new GuardrailProviderException(throwFailureCode);
            }
            return successResult;
        }
    }

    private static final class FakeOutboxPort implements OutboxPort {
        private final List<OutboxEventRecord> events = new ArrayList<>();

        List<OutboxEventRecord> savedEvents() {
            return events;
        }

        @Override
        public void save(OutboxEventRecord event) {
            events.add(event);
        }

        @Override
        public List<OutboxEventRecord> findPendingBatch(int batchSize) {
            return events.stream().limit(batchSize).toList();
        }

        @Override
        public void markPublished(UUID eventId) {
            // not used
        }
    }
}
