package com.guardbench.evaluation.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.guardbench.evaluation.application.FinalizeTestRunService.FinalizationOutcome;
import com.guardbench.evaluation.application.port.out.FinalizeTestRunPort;
import com.guardbench.evaluation.application.port.out.LoadTestRunExecutionFactsPort;
import com.guardbench.evaluation.application.port.out.TestRunExecutionFacts;
import com.guardbench.evaluation.application.port.out.TestRunExecutionFacts.SnapshotExecutionFact;
import com.guardbench.evaluation.application.port.out.TestRunExecutionFacts.TargetExecutionFact;
import com.guardbench.evaluation.domain.QualityGateEvaluator;
import com.guardbench.evaluation.domain.QualityGateResult;
import com.guardbench.evaluation.domain.QualityGateStatus;
import com.guardbench.evaluation.domain.SnapshotEvaluation;
import com.guardbench.evaluation.domain.SnapshotEvaluationReference;
import com.guardbench.evaluation.domain.SnapshotEvaluator;
import com.guardbench.evaluation.domain.TestRunEvaluationReference;
import com.guardbench.evaluation.domain.repository.QualityGateResultRepository;
import com.guardbench.evaluation.domain.repository.SnapshotEvaluationRepository;

class FinalizeTestRunServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-08-26T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final long TEST_RUN_ID = 1L;

    private FakeLoadExecutionFactsPort loadFactsPort;
    private FakeFinalizeTestRunPort finalizeTestRunPort;
    private FakeQualityGateResultRepository qualityGateResultRepo;
    private FakeSnapshotEvaluationRepository snapshotEvaluationRepo;
    private FinalizeTestRunService service;

    @BeforeEach
    void setUp() {
        loadFactsPort = new FakeLoadExecutionFactsPort();
        finalizeTestRunPort = new FakeFinalizeTestRunPort();
        qualityGateResultRepo = new FakeQualityGateResultRepository();
        snapshotEvaluationRepo = new FakeSnapshotEvaluationRepository();
        service = new FinalizeTestRunService(
                loadFactsPort,
                finalizeTestRunPort,
                qualityGateResultRepo,
                snapshotEvaluationRepo,
                new SnapshotEvaluator(),
                new QualityGateEvaluator(),
                FIXED_CLOCK
        );
    }

    @Nested
    @DisplayName("현재 TestRun 기반 Quality Gate")
    class PassScenario {

        @Test
        @DisplayName("모든 Assertion이 통과하고 실행이 성공하면 PASS다")
        void allAssertionsPass() {
            loadFactsPort.setFacts(TEST_RUN_ID, runningFacts(2, List.of(
                    succeededPair(10L, "BLOCK", "BLOCK", "BLOCK"),
                    succeededPair(20L, "BLOCK", "BLOCK", "BLOCK")
            )));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.Finalized.class, outcome);
            QualityGateResult result = ((FinalizationOutcome.Finalized) outcome).result();
            assertEquals(QualityGateStatus.PASS, result.status());
            assertEquals(1.0, result.metrics().assertionPassRate());
            assertEquals(1.0, result.metrics().executionSuccessRate());

            assertEquals(1, finalizeTestRunPort.callCount());
            assertEquals("COMPLETED", finalizeTestRunPort.lastOutcomeCode());
            assertEquals(2, finalizeTestRunPort.lastProcessedTestCaseCount());
            assertEquals(1, qualityGateResultRepo.savedResults().size());
            assertEquals(2, snapshotEvaluationRepo.savedEvaluations().size());
        }
    }

    @Nested
    @DisplayName("INCOMPLETE - 부분 실행 실패")
    class IncompleteScenario {

        @Test
        @DisplayName("일부 실행이 FAILED terminal이면 outcome=INCOMPLETE로 최종화한다")
        void incompleteExecution() {
            loadFactsPort.setFacts(TEST_RUN_ID, runningFacts(2, List.of(
                    succeededPair(10L, "BLOCK", "BLOCK", "BLOCK"),
                    new SnapshotExecutionFact(20L, "BLOCK", terminal("FAILED", null))
            )));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.Finalized.class, outcome);
            assertNotNull(((FinalizationOutcome.Finalized) outcome).result());
            assertEquals("INCOMPLETE", finalizeTestRunPort.lastOutcomeCode());
            assertEquals(2, finalizeTestRunPort.lastProcessedTestCaseCount());
        }
    }

    @Nested
    @DisplayName("NOT_EVALUATED - 평가 가능 데이터 없음")
    class NotEvaluatedScenario {

        @Test
        @DisplayName("ChangeResult가 없어도 Assertion으로 Quality Gate를 평가한다")
        void evaluatesWithoutChangeResults() {
            loadFactsPort.setFacts(TEST_RUN_ID, runningFacts(2, List.of(
                    new SnapshotExecutionFact(10L, "BLOCK", terminal("SUCCEEDED", "BLOCK")),
                    new SnapshotExecutionFact(20L, "BLOCK", terminal("SUCCEEDED", "BLOCK"))
            )));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.Finalized.class, outcome);
            QualityGateResult result = ((FinalizationOutcome.Finalized) outcome).result();
            assertEquals(QualityGateStatus.PASS, result.status());
            assertEquals(1.0, result.metrics().assertionPassRate());
            assertEquals(1.0, result.metrics().executionSuccessRate());
        }

        @Test
        @DisplayName("모든 실행이 NOT_STARTED terminal이면 outcome=ERROR로 최종화한다")
        void notStartedExecutionsFinalizeAsError() {
            loadFactsPort.setFacts(TEST_RUN_ID, runningFacts(1, List.of(
                    new SnapshotExecutionFact(10L, "BLOCK", terminal("NOT_STARTED", null))
            )));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.Finalized.class, outcome);
            assertEquals(QualityGateStatus.NOT_EVALUATED,
                    ((FinalizationOutcome.Finalized) outcome).result().status());
            assertEquals("ERROR", finalizeTestRunPort.lastOutcomeCode());
        }
    }

    @Nested
    @DisplayName("조기 최종화 방지")
    class ReadinessScenario {

        @Test
        @DisplayName("실행 결과가 아직 없으면 최종화하지 않지만 진행도는 갱신한다")
        void notReadyWhenExecutionIsMissing() {
            loadFactsPort.setFacts(TEST_RUN_ID, runningFacts(2, List.of(
                    succeededPair(10L, "BLOCK", "BLOCK", "BLOCK"),
                    new SnapshotExecutionFact(20L, "BLOCK", TargetExecutionFact.notExecuted())
            )));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.NotReady.class, outcome);
            assertEquals(0, qualityGateResultRepo.savedResults().size());
            assertEquals(0, snapshotEvaluationRepo.newlySavedCount());
            assertEquals(0, finalizeTestRunPort.callCount());
            assertEquals(1, finalizeTestRunPort.progressUpdateCallCount());
        }

        @Test
        @DisplayName("Snapshot이 TestCase 수만큼 준비되지 않았으면 최종화도 진행도 갱신도 하지 않는다")
        void notReadyWhenSnapshotsIncomplete() {
            loadFactsPort.setFacts(TEST_RUN_ID, runningFacts(3, List.of(
                    succeededPair(10L, "BLOCK", "BLOCK", "BLOCK")
            )));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.NotReady.class, outcome);
            assertEquals(0, qualityGateResultRepo.savedResults().size());
            assertEquals(0, finalizeTestRunPort.callCount());
            assertEquals(0, finalizeTestRunPort.progressUpdateCallCount());
        }

        @Test
        @DisplayName("마지막 완료로 모든 실행이 terminal이 되면 최종화한다")
        void finalizesWhenLastExecutionBecomesTerminal() {
            loadFactsPort.setFacts(TEST_RUN_ID, runningFacts(2, List.of(
                    succeededPair(10L, "BLOCK", "BLOCK", "BLOCK"),
                    new SnapshotExecutionFact(20L, "BLOCK", TargetExecutionFact.notExecuted())
            )));
            assertInstanceOf(FinalizationOutcome.NotReady.class, service.finalize(TEST_RUN_ID));

            loadFactsPort.setFacts(TEST_RUN_ID, runningFacts(2, List.of(
                    succeededPair(10L, "BLOCK", "BLOCK", "BLOCK"),
                    succeededPair(20L, "BLOCK", "BLOCK", "BLOCK")
            )));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.Finalized.class, outcome);
            assertEquals(1, finalizeTestRunPort.callCount());
            assertEquals(2, finalizeTestRunPort.lastProcessedTestCaseCount());
        }
    }

    @Nested
    @DisplayName("멱등성 - 이미 완료된 최종화의 재호출")
    class IdempotencyScenario {

        @Test
        @DisplayName("이미 FINISHED이고 QualityGateResult가 있으면 기존 결과를 반환한다")
        void alreadyFinalizedReturnsExistingResult() {
            QualityGateResult existing = new QualityGateResult(
                    new TestRunEvaluationReference(TEST_RUN_ID),
                    QualityGateStatus.PASS,
                    new com.guardbench.evaluation.domain.QualityGateMetrics(1.0, 1.0),
                    FIXED_NOW.minusSeconds(60)
            );
            qualityGateResultRepo.preStore(existing);
            loadFactsPort.setFacts(TEST_RUN_ID, new TestRunExecutionFacts(
                    TEST_RUN_ID, "FINISHED", 2, List.of(
                    succeededPair(10L, "BLOCK", "BLOCK", "BLOCK"),
                    succeededPair(20L, "BLOCK", "BLOCK", "BLOCK"))));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.AlreadyFinalized.class, outcome);
            QualityGateResult returned = ((FinalizationOutcome.AlreadyFinalized) outcome).result();
            assertEquals(existing, returned);

            assertEquals(1, qualityGateResultRepo.savedResults().size());
            assertEquals(0, finalizeTestRunPort.callCount());
        }
    }

    @Nested
    @DisplayName("중복/순서 무관 완료 메시지")
    class DuplicateOutOfOrder {

        @Test
        @DisplayName("이미 평가된 Snapshot은 기존 결과를 재사용하고 재계산하지 않는다")
        void existingSnapshotEvaluationIsReused() {
            SnapshotEvaluation preExisting = new SnapshotEvaluation(
                    new SnapshotEvaluationReference(10L),
                    new com.guardbench.evaluation.domain.AssertionResult(
                            com.guardbench.evaluation.domain.AssertionStatus.PASS),
                    com.guardbench.evaluation.domain.ChangeResult.comparable(
                            com.guardbench.evaluation.domain.ChangeType.NO_CHANGE),
                    FIXED_NOW.minusSeconds(10)
            );
            snapshotEvaluationRepo.preStore(preExisting);

            loadFactsPort.setFacts(TEST_RUN_ID, runningFacts(2, List.of(
                    succeededPair(10L, "BLOCK", "BLOCK", "BLOCK"),
                    succeededPair(20L, "BLOCK", "BLOCK", "BLOCK")
            )));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.Finalized.class, outcome);
            assertEquals(1, snapshotEvaluationRepo.newlySavedCount());
        }
    }

    @Nested
    @DisplayName("상태 검증")
    class StateValidation {

        @Test
        @DisplayName("TestRun이 존재하지 않으면 NotFound")
        void notFoundWhenNoTestRun() {
            FinalizationOutcome outcome = service.finalize(999L);
            assertInstanceOf(FinalizationOutcome.NotFound.class, outcome);
        }

        @Test
        @DisplayName("RUNNING이 아닌 상태에서는 NotReady")
        void notReadyWhenNotRunning() {
            loadFactsPort.setFacts(TEST_RUN_ID, new TestRunExecutionFacts(
                    TEST_RUN_ID, "PREPARING", 2, List.of()));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);
            assertInstanceOf(FinalizationOutcome.NotReady.class, outcome);
        }

        @Test
        @DisplayName("FINISHED인데 QualityGateResult가 없으면 InvariantViolation")
        void invariantViolationWhenFinishedWithoutResult() {
            loadFactsPort.setFacts(TEST_RUN_ID, new TestRunExecutionFacts(
                    TEST_RUN_ID, "FINISHED", 2, List.of()));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);
            assertInstanceOf(FinalizationOutcome.InvariantViolation.class, outcome);
        }
    }

    // ─── Fixture Helper ───────────────────────────────────────────────────────

    private static TestRunExecutionFacts runningFacts(int testCaseCount, List<SnapshotExecutionFact> facts) {
        return new TestRunExecutionFacts(TEST_RUN_ID, "RUNNING", testCaseCount, facts);
    }

    private static SnapshotExecutionFact succeededPair(
            long snapshotId,
            String expectedActionCode,
            String ignoredBaselineActionCode,
            String targetActionCode
    ) {
        return new SnapshotExecutionFact(
                snapshotId,
                expectedActionCode,
                terminal("SUCCEEDED", targetActionCode)
        );
    }

    private static TargetExecutionFact terminal(String statusCode, String actionCode) {
        return new TargetExecutionFact(true, statusCode, actionCode);
    }

    // ─── Fake Adapters ────────────────────────────────────────────────────────

    private static final class FakeLoadExecutionFactsPort implements LoadTestRunExecutionFactsPort {
        private final Map<Long, TestRunExecutionFacts> factsMap = new HashMap<>();

        void setFacts(long testRunId, TestRunExecutionFacts facts) {
            factsMap.put(testRunId, facts);
        }

        @Override
        public Optional<TestRunExecutionFacts> lockAndLoad(long testRunId) {
            return Optional.ofNullable(factsMap.get(testRunId));
        }
    }

    private static final class FakeFinalizeTestRunPort implements FinalizeTestRunPort {
        private int callCount;
        private String lastOutcomeCode;
        private int lastProcessedTestCaseCount;
        private int progressUpdateCallCount;

        int callCount() { return callCount; }
        String lastOutcomeCode() { return lastOutcomeCode; }
        int lastProcessedTestCaseCount() { return lastProcessedTestCaseCount; }
        int progressUpdateCallCount() { return progressUpdateCallCount; }

        @Override
        public void finalize(long testRunId, String executionOutcomeCode, int processedTestCaseCount, int testCaseCount) {
            callCount++;
            lastOutcomeCode = executionOutcomeCode;
            lastProcessedTestCaseCount = processedTestCaseCount;
        }

        @Override
        public void updateProgress(long testRunId) {
            progressUpdateCallCount++;
        }
    }

    private static final class FakeQualityGateResultRepository implements QualityGateResultRepository {
        private final Map<Long, QualityGateResult> store = new HashMap<>();
        private final List<QualityGateResult> savedList = new ArrayList<>();

        void preStore(QualityGateResult result) {
            store.put(result.reference().value(), result);
            savedList.add(result);
        }

        List<QualityGateResult> savedResults() { return savedList; }

        @Override
        public Optional<QualityGateResult> findById(TestRunEvaluationReference testRunId) {
            return Optional.ofNullable(store.get(testRunId.value()));
        }

        @Override
        public void save(QualityGateResult result) {
            store.put(result.reference().value(), result);
            savedList.add(result);
        }
    }

    private static final class FakeSnapshotEvaluationRepository implements SnapshotEvaluationRepository {
        private final Map<Long, SnapshotEvaluation> store = new HashMap<>();
        private int newlySaved;

        void preStore(SnapshotEvaluation evaluation) {
            store.put(evaluation.reference().value(), evaluation);
        }

        int newlySavedCount() { return newlySaved; }
        List<SnapshotEvaluation> savedEvaluations() { return new ArrayList<>(store.values()); }

        @Override
        public Optional<SnapshotEvaluation> findById(SnapshotEvaluationReference snapshotId) {
            return Optional.ofNullable(store.get(snapshotId.value()));
        }

        @Override
        public void save(SnapshotEvaluation evaluation) {
            store.put(evaluation.reference().value(), evaluation);
            newlySaved++;
        }
    }
}
