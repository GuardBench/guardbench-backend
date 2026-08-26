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

import com.guardbench.testrun.application.port.out.ClaimResult;
import com.guardbench.testrun.application.port.out.GuardrailFailureCode;
import com.guardbench.testrun.application.port.out.GuardrailMaterializationPort;
import com.guardbench.testrun.application.port.out.GuardrailMaterializationRequest;
import com.guardbench.testrun.application.port.out.GuardrailMaterializedVersion;
import com.guardbench.testrun.application.port.out.GuardrailProviderException;
import com.guardbench.testrun.application.port.out.LoadSnapshotIdsByTestRunPort;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.ResolutionClaimPort;
import com.guardbench.testrun.domain.BaselineTarget;
import com.guardbench.testrun.domain.CandidateSource;
import com.guardbench.testrun.domain.CandidateTarget;
import com.guardbench.testrun.domain.SourceTestSuiteId;
import com.guardbench.testrun.domain.TargetType;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestExecution;
import com.guardbench.testrun.domain.TestExecutionId;
import com.guardbench.testrun.domain.TestExecutionStatus;
import com.guardbench.testrun.domain.TestRun;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.TestRunExecutionOutcome;
import com.guardbench.testrun.domain.TestRunStatus;
import com.guardbench.testrun.domain.repository.TestExecutionRepository;
import com.guardbench.testrun.domain.repository.TestRunRepository;

class ResolveTestRunServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final long TEST_RUN_ID = 1L;
    private static final String GUARDRAIL_ID = "my-guardrail";

    private FakeResolutionClaimPort claimPort;
    private FakeTestRunRepository testRunRepository;
    private FakeMaterializationPort materializationPort;
    private FakeLoadSnapshotIdsPort loadSnapshotIdsPort;
    private FakeOutboxPort outboxPort;
    private FakeTestExecutionRepository executionRepository;
    private ResolveTestRunService service;

    @BeforeEach
    void setUp() {
        claimPort = new FakeResolutionClaimPort();
        testRunRepository = new FakeTestRunRepository();
        materializationPort = new FakeMaterializationPort();
        loadSnapshotIdsPort = new FakeLoadSnapshotIdsPort();
        outboxPort = new FakeOutboxPort();
        executionRepository = new FakeTestExecutionRepository();
        service = new ResolveTestRunService(
                claimPort, testRunRepository, materializationPort,
                loadSnapshotIdsPort, outboxPort, executionRepository, FIXED_CLOCK
        );
    }

    @Nested
    @DisplayName("정상 Resolution 흐름")
    class HappyPath {

        @Test
        @DisplayName("QUEUED TestRun을 RUNNING으로 전환하고 fan-out Outbox를 생성한다")
        void resolvesQueuedTestRun() {
            TestRun testRun = queuedTestRun(TEST_RUN_ID, 3);
            testRunRepository.store(testRun);
            claimPort.willAcquire(TEST_RUN_ID);
            materializationPort.willReturn(new GuardrailMaterializedVersion(GUARDRAIL_ID, "5"));
            loadSnapshotIdsPort.setSnapshotIds(TEST_RUN_ID, List.of(10L, 20L, 30L));

            ResolveTestRunService.ResolutionOutcome outcome = service.resolve(TEST_RUN_ID);

            assertEquals(ResolveTestRunService.ResolutionOutcome.RESOLVED, outcome);

            TestRun saved = testRunRepository.findById(new TestRunId(TEST_RUN_ID)).orElseThrow();
            assertEquals(TestRunStatus.RUNNING, saved.status());
            assertEquals("5", saved.candidateTarget().resolvedVersion());

            // 3 snapshots × 2 targets = 6 outbox events
            assertEquals(6, outboxPort.savedEvents().size());
            assertAllEventTypesAre("TestExecutionRequested", outboxPort.savedEvents());
            assertDeduplicationKeysUnique(outboxPort.savedEvents());
        }

        @Test
        @DisplayName("PREPARING 상태에서도 materialization을 진행한다")
        void resolvesPreparing() {
            TestRun testRun = queuedTestRun(TEST_RUN_ID, 2);
            testRun.beginPreparing(FIXED_NOW.minusSeconds(10));
            testRunRepository.store(testRun);
            claimPort.willAcquire(TEST_RUN_ID);
            materializationPort.willReturn(new GuardrailMaterializedVersion(GUARDRAIL_ID, "3"));
            loadSnapshotIdsPort.setSnapshotIds(TEST_RUN_ID, List.of(100L, 200L));

            ResolveTestRunService.ResolutionOutcome outcome = service.resolve(TEST_RUN_ID);

            assertEquals(ResolveTestRunService.ResolutionOutcome.RESOLVED, outcome);
            assertEquals(4, outboxPort.savedEvents().size());
        }
    }

    @Nested
    @DisplayName("멱등성과 중복 처리")
    class Idempotency {

        @Test
        @DisplayName("RUNNING TestRun에 대해 ALREADY_RESOLVED를 반환한다")
        void alreadyRunning() {
            TestRun testRun = runningTestRun(TEST_RUN_ID);
            testRunRepository.store(testRun);

            ResolveTestRunService.ResolutionOutcome outcome = service.resolve(TEST_RUN_ID);

            assertEquals(ResolveTestRunService.ResolutionOutcome.ALREADY_RESOLVED, outcome);
            assertTrue(outboxPort.savedEvents().isEmpty());
        }

        @Test
        @DisplayName("FINISHED TestRun에 대해 ALREADY_RESOLVED를 반환한다")
        void alreadyFinished() {
            TestRun testRun = finishedTestRun(TEST_RUN_ID);
            testRunRepository.store(testRun);

            ResolveTestRunService.ResolutionOutcome outcome = service.resolve(TEST_RUN_ID);

            assertEquals(ResolveTestRunService.ResolutionOutcome.ALREADY_RESOLVED, outcome);
        }

        @Test
        @DisplayName("존재하지 않는 TestRun에 대해 NOT_FOUND를 반환한다")
        void notFound() {
            ResolveTestRunService.ResolutionOutcome outcome = service.resolve(999L);

            assertEquals(ResolveTestRunService.ResolutionOutcome.NOT_FOUND, outcome);
        }
    }

    @Nested
    @DisplayName("Claim 경합")
    class ClaimContention {

        @Test
        @DisplayName("다른 Worker가 claim을 보유하면 CLAIM_HELD_BY_OTHER를 반환한다")
        void claimHeldByOther() {
            TestRun testRun = queuedTestRun(TEST_RUN_ID, 2);
            testRunRepository.store(testRun);
            claimPort.willBeHeld(TEST_RUN_ID);

            ResolveTestRunService.ResolutionOutcome outcome = service.resolve(TEST_RUN_ID);

            assertEquals(ResolveTestRunService.ResolutionOutcome.CLAIM_HELD_BY_OTHER, outcome);
            // TestRun 상태가 바뀌지 않았다
            assertEquals(TestRunStatus.QUEUED, testRunRepository.findById(new TestRunId(TEST_RUN_ID)).orElseThrow().status());
        }

        @Test
        @DisplayName("materialization 후 claim을 잃으면 CLAIM_LOST_AFTER_MATERIALIZATION을 반환한다")
        void claimLostAfterMaterialization() {
            TestRun testRun = queuedTestRun(TEST_RUN_ID, 2);
            testRunRepository.store(testRun);
            claimPort.willAcquire(TEST_RUN_ID);
            claimPort.setIsHeldByResult(false); // isHeldBy 재검증 실패
            materializationPort.willReturn(new GuardrailMaterializedVersion(GUARDRAIL_ID, "5"));

            ResolveTestRunService.ResolutionOutcome outcome = service.resolve(TEST_RUN_ID);

            assertEquals(ResolveTestRunService.ResolutionOutcome.CLAIM_LOST_AFTER_MATERIALIZATION, outcome);
            // fan-out Outbox가 없다
            assertTrue(outboxPort.savedEvents().isEmpty());
        }
    }

    @Nested
    @DisplayName("Materialization 실패")
    class MaterializationFailure {

        @Test
        @DisplayName("attempt 한도 미초과 시 RETRYABLE을 반환한다")
        void retryableFailure() {
            TestRun testRun = queuedTestRun(TEST_RUN_ID, 2);
            testRunRepository.store(testRun);
            claimPort.willAcquireWithAttempt(TEST_RUN_ID, 1); // 첫 시도
            materializationPort.willFail(GuardrailFailureCode.PROVIDER_UNAVAILABLE);

            ResolveTestRunService.ResolutionOutcome outcome = service.resolve(TEST_RUN_ID);

            assertEquals(ResolveTestRunService.ResolutionOutcome.MATERIALIZATION_FAILED_RETRYABLE, outcome);
            // TestRun은 PREPARING 상태에 머문다
            assertEquals(TestRunStatus.PREPARING,
                    testRunRepository.findById(new TestRunId(TEST_RUN_ID)).orElseThrow().status());
        }

        @Test
        @DisplayName("attempt 한도 초과 시 모든 execution NOT_STARTED + TestRun FINISHED/ERROR로 종결한다")
        void terminalFailure() {
            TestRun testRun = queuedTestRun(TEST_RUN_ID, 2);
            testRunRepository.store(testRun);
            claimPort.willAcquireWithAttempt(TEST_RUN_ID, 3); // 최대 시도 도달
            materializationPort.willFail(GuardrailFailureCode.TARGET_NOT_FOUND);
            loadSnapshotIdsPort.setSnapshotIds(TEST_RUN_ID, List.of(10L, 20L));

            ResolveTestRunService.ResolutionOutcome outcome = service.resolve(TEST_RUN_ID);

            assertEquals(ResolveTestRunService.ResolutionOutcome.MATERIALIZATION_FAILED_TERMINAL, outcome);

            // TestRun FINISHED/ERROR
            TestRun saved = testRunRepository.findById(new TestRunId(TEST_RUN_ID)).orElseThrow();
            assertEquals(TestRunStatus.FINISHED, saved.status());
            assertEquals(TestRunExecutionOutcome.ERROR, saved.executionOutcome());

            // 4개의 NOT_STARTED TestExecution (2 snapshots × 2 targets)
            assertEquals(4, executionRepository.savedExecutions().size());
            for (TestExecution exec : executionRepository.savedExecutions()) {
                assertEquals(TestExecutionStatus.NOT_STARTED, exec.status());
            }
        }
    }

    @Nested
    @DisplayName("Deduplication key 계약")
    class DeduplicationKeys {

        @Test
        @DisplayName("fan-out dedup key는 eventType:snapshotId:targetType 형식이다")
        void deduplicationKeyFormat() {
            TestRun testRun = queuedTestRun(TEST_RUN_ID, 1);
            testRunRepository.store(testRun);
            claimPort.willAcquire(TEST_RUN_ID);
            materializationPort.willReturn(new GuardrailMaterializedVersion(GUARDRAIL_ID, "2"));
            loadSnapshotIdsPort.setSnapshotIds(TEST_RUN_ID, List.of(42L));

            service.resolve(TEST_RUN_ID);

            List<OutboxEventRecord> events = outboxPort.savedEvents();
            assertEquals(2, events.size());
            assertTrue(events.stream().anyMatch(e ->
                    e.deduplicationKey().equals("TestExecutionRequested:42:BASELINE")));
            assertTrue(events.stream().anyMatch(e ->
                    e.deduplicationKey().equals("TestExecutionRequested:42:CANDIDATE")));
        }

        @Test
        @DisplayName("materialization request token은 guardbench-test-run-{id} 형식이다")
        void materializationToken() {
            TestRun testRun = queuedTestRun(TEST_RUN_ID, 1);
            testRunRepository.store(testRun);
            claimPort.willAcquire(TEST_RUN_ID);
            materializationPort.willReturn(new GuardrailMaterializedVersion(GUARDRAIL_ID, "1"));
            loadSnapshotIdsPort.setSnapshotIds(TEST_RUN_ID, List.of(1L));

            service.resolve(TEST_RUN_ID);

            assertNotNull(materializationPort.lastRequest());
            assertEquals("guardbench-test-run-" + TEST_RUN_ID,
                    materializationPort.lastRequest().clientRequestToken());
        }
    }

    // ─── Test Fixtures ────────────────────────────────────────────────────────

    private static TestRun queuedTestRun(long id, int testCaseCount) {
        return TestRun.queue(
                new TestRunId(id),
                new SourceTestSuiteId(1L),
                new BaselineTarget(GUARDRAIL_ID, "1"),
                new CandidateTarget(GUARDRAIL_ID, CandidateSource.DRAFT, null),
                testCaseCount,
                FIXED_NOW.minusSeconds(60)
        );
    }

    private static TestRun runningTestRun(long id) {
        TestRun testRun = queuedTestRun(id, 2);
        testRun.beginPreparing(FIXED_NOW.minusSeconds(30));
        testRun.beginRunning("3", FIXED_NOW.minusSeconds(20));
        return testRun;
    }

    private static TestRun finishedTestRun(long id) {
        TestRun testRun = queuedTestRun(id, 2);
        testRun.beginPreparing(FIXED_NOW.minusSeconds(30));
        testRun.failPreparation(FIXED_NOW.minusSeconds(10));
        return testRun;
    }

    // ─── Fake Adapters ────────────────────────────────────────────────────────

    private static final class FakeResolutionClaimPort implements ResolutionClaimPort {
        private final Map<Long, ClaimResult> acquireResults = new HashMap<>();
        private boolean isHeldByResult = true; // default: claim is still held after materialization
        private UUID lastToken;

        void willAcquire(long testRunId) {
            willAcquireWithAttempt(testRunId, 1);
        }

        void willAcquireWithAttempt(long testRunId, int attemptCount) {
            UUID token = UUID.randomUUID();
            lastToken = token;
            acquireResults.put(testRunId, new ClaimResult.Acquired(token, attemptCount));
        }

        void willBeHeld(long testRunId) {
            acquireResults.put(testRunId, new ClaimResult.AlreadyHeld());
        }

        void setIsHeldByResult(boolean result) {
            this.isHeldByResult = result;
        }

        @Override
        public ClaimResult tryAcquire(long testRunId) {
            return acquireResults.getOrDefault(testRunId, new ClaimResult.AlreadyHeld());
        }

        @Override
        public boolean isHeldBy(long testRunId, UUID claimToken) {
            return isHeldByResult;
        }
    }

    private static final class FakeTestRunRepository implements TestRunRepository {
        private final Map<Long, TestRun> runs = new HashMap<>();

        void store(TestRun testRun) {
            runs.put(testRun.id().value(), testRun);
        }

        @Override
        public Optional<TestRun> findById(TestRunId id) {
            return Optional.ofNullable(runs.get(id.value()));
        }

        @Override
        public void save(TestRun testRun) {
            runs.put(testRun.id().value(), testRun);
        }
    }

    private static final class FakeMaterializationPort implements GuardrailMaterializationPort {
        private GuardrailMaterializedVersion successResult;
        private GuardrailFailureCode failureCode;
        private GuardrailMaterializationRequest lastRequest;

        void willReturn(GuardrailMaterializedVersion version) {
            this.successResult = version;
            this.failureCode = null;
        }

        void willFail(GuardrailFailureCode code) {
            this.failureCode = code;
            this.successResult = null;
        }

        GuardrailMaterializationRequest lastRequest() {
            return lastRequest;
        }

        @Override
        public GuardrailMaterializedVersion materialize(GuardrailMaterializationRequest request) {
            this.lastRequest = request;
            if (failureCode != null) {
                throw new GuardrailProviderException(failureCode);
            }
            return successResult;
        }
    }

    private static final class FakeLoadSnapshotIdsPort implements LoadSnapshotIdsByTestRunPort {
        private final Map<Long, List<Long>> snapshotIdsByTestRunId = new HashMap<>();

        void setSnapshotIds(long testRunId, List<Long> ids) {
            snapshotIdsByTestRunId.put(testRunId, ids);
        }

        @Override
        public List<Long> loadSnapshotIdsByTestRunId(long testRunId) {
            return snapshotIdsByTestRunId.getOrDefault(testRunId, List.of());
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
            // not used in resolution tests
        }
    }

    private static final class FakeTestExecutionRepository implements TestExecutionRepository {
        private final List<TestExecution> executions = new ArrayList<>();

        List<TestExecution> savedExecutions() {
            return executions;
        }

        @Override
        public Optional<TestExecution> findById(TestExecutionId id) {
            return executions.stream()
                    .filter(e -> e.id().equals(id))
                    .findFirst();
        }

        @Override
        public void save(TestExecution execution) {
            executions.add(execution);
        }
    }

    // ─── Assertion Helpers ─────────────────────────────────────────────────────

    private static void assertAllEventTypesAre(String expectedType, List<OutboxEventRecord> events) {
        for (OutboxEventRecord event : events) {
            assertEquals(expectedType, event.eventType());
        }
    }

    private static void assertDeduplicationKeysUnique(List<OutboxEventRecord> events) {
        long uniqueKeys = events.stream()
                .map(OutboxEventRecord::deduplicationKey)
                .distinct()
                .count();
        assertEquals(events.size(), uniqueKeys, "Deduplication keys must be unique");
    }
}
