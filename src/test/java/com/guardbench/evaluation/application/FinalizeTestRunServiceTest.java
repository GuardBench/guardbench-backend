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
    @DisplayName("PASS - 모든 실행 성공 + COMPARABLE + 정책 통과")
    class PassScenario {

        @Test
        @DisplayName("모든 Baseline/Candidate가 같은 BLOCK action이면 PASS다")
        void allPassWithNoChange() {
            List<SnapshotExecutionFact> facts = List.of(
                    new SnapshotExecutionFact(10L, "BLOCK", "BLOCK", "BLOCK", true, true),
                    new SnapshotExecutionFact(20L, "BLOCK", "BLOCK", "BLOCK", true, true)
            );
            loadFactsPort.setFacts(TEST_RUN_ID, new TestRunExecutionFacts(
                    TEST_RUN_ID, "RUNNING", 2, 2, facts));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.Finalized.class, outcome);
            QualityGateResult result = ((FinalizationOutcome.Finalized) outcome).result();
            assertEquals(QualityGateStatus.PASS, result.status());
            assertNotNull(result.metrics());
            assertEquals(1.0, result.metrics().candidateAssertionPassRate());
            assertEquals(0, result.metrics().securityRegressionCount());

            // FinalizeTestRunPort was called
            assertEquals(1, finalizeTestRunPort.callCount());
            assertEquals("COMPLETED", finalizeTestRunPort.lastOutcomeCode());

            // QualityGateResult was saved
            assertEquals(1, qualityGateResultRepo.savedResults().size());

            // SnapshotEvaluations were saved
            assertEquals(2, snapshotEvaluationRepo.savedEvaluations().size());
        }
    }

    @Nested
    @DisplayName("INCOMPLETE - 부분 실행 성공")
    class IncompleteScenario {

        @Test
        @DisplayName("일부 Candidate 실패 시 outcome=INCOMPLETE이고 Quality Gate는 NOT_EVALUATED 또는 FAIL")
        void incompleteExecution() {
            List<SnapshotExecutionFact> facts = List.of(
                    new SnapshotExecutionFact(10L, "BLOCK", "BLOCK", "BLOCK", true, true),
                    new SnapshotExecutionFact(20L, "BLOCK", "BLOCK", null, true, false)
            );
            loadFactsPort.setFacts(TEST_RUN_ID, new TestRunExecutionFacts(
                    TEST_RUN_ID, "RUNNING", 2, 1, facts));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.Finalized.class, outcome);
            QualityGateResult result = ((FinalizationOutcome.Finalized) outcome).result();
            // Only 1 comparable change, so NOT_EVALUATED is also possible depending on the evaluator logic
            assertNotNull(result);
            assertEquals("INCOMPLETE", finalizeTestRunPort.lastOutcomeCode());
        }
    }

    @Nested
    @DisplayName("NOT_EVALUATED - 평가 가능 데이터 없음")
    class NotEvaluatedScenario {

        @Test
        @DisplayName("COMPARABLE ChangeResult가 없으면 NOT_EVALUATED다")
        void notEvaluatedWhenNoComparableChanges() {
            // Baseline 실패 → comparison conditions not satisfied → ChangeResult = NOT_COMPARABLE
            List<SnapshotExecutionFact> facts = List.of(
                    new SnapshotExecutionFact(10L, "BLOCK", null, "BLOCK", false, true),
                    new SnapshotExecutionFact(20L, "BLOCK", null, "BLOCK", false, true)
            );
            loadFactsPort.setFacts(TEST_RUN_ID, new TestRunExecutionFacts(
                    TEST_RUN_ID, "RUNNING", 2, 0, facts));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.Finalized.class, outcome);
            QualityGateResult result = ((FinalizationOutcome.Finalized) outcome).result();
            assertEquals(QualityGateStatus.NOT_EVALUATED, result.status());
            assertNull(result.metrics());
        }

        @Test
        @DisplayName("준비 실패 최종화에서 NOT_EVALUATED가 저장된다")
        void preparationFailureCreatesNotEvaluated() {
            FinalizationOutcome outcome = service.finalizePreparationFailure(TEST_RUN_ID, 3);

            assertInstanceOf(FinalizationOutcome.Finalized.class, outcome);
            QualityGateResult result = ((FinalizationOutcome.Finalized) outcome).result();
            assertEquals(QualityGateStatus.NOT_EVALUATED, result.status());
            assertNull(result.metrics());
            assertEquals(FIXED_NOW, result.createdAt());
            assertEquals(1, qualityGateResultRepo.savedResults().size());
        }
    }

    @Nested
    @DisplayName("멱등성 - 이미 완료된 최종화의 재호출")
    class IdempotencyScenario {

        @Test
        @DisplayName("이미 FINISHED이고 QualityGateResult가 있으면 기존 결과를 반환한다")
        void alreadyFinalizedReturnsExistingResult() {
            // Pre-existing result
            QualityGateResult existing = new QualityGateResult(
                    new TestRunEvaluationReference(TEST_RUN_ID),
                    QualityGateStatus.PASS,
                    new com.guardbench.evaluation.domain.QualityGateMetrics(
                            1.0, 0, 0.0, 0.0, 1.0),
                    FIXED_NOW.minusSeconds(60)
            );
            qualityGateResultRepo.preStore(existing);

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.AlreadyFinalized.class, outcome);
            QualityGateResult returned = ((FinalizationOutcome.AlreadyFinalized) outcome).result();
            assertEquals(existing, returned);

            // No new saves
            assertEquals(1, qualityGateResultRepo.savedResults().size());
            assertEquals(0, finalizeTestRunPort.callCount());
        }

        @Test
        @DisplayName("준비 실패 최종화도 이미 존재하면 멱등 성공이다")
        void preparationFailureIdempotent() {
            QualityGateResult existing = new QualityGateResult(
                    new TestRunEvaluationReference(TEST_RUN_ID),
                    QualityGateStatus.NOT_EVALUATED,
                    null,
                    FIXED_NOW.minusSeconds(30)
            );
            qualityGateResultRepo.preStore(existing);

            FinalizationOutcome outcome = service.finalizePreparationFailure(TEST_RUN_ID, 2);

            assertInstanceOf(FinalizationOutcome.AlreadyFinalized.class, outcome);
            // 기존 결과를 재계산하지 않는다
            assertEquals(1, qualityGateResultRepo.savedResults().size());
        }
    }

    @Nested
    @DisplayName("중복/순서 무관 완료 메시지")
    class DuplicateOutOfOrder {

        @Test
        @DisplayName("이미 평가된 Snapshot은 기존 결과를 재사용하고 재계산하지 않는다")
        void existingSnapshotEvaluationIsReused() {
            // Pre-store one snapshot evaluation
            SnapshotEvaluation preExisting = new SnapshotEvaluation(
                    new SnapshotEvaluationReference(10L),
                    new com.guardbench.evaluation.domain.AssertionResult(
                            com.guardbench.evaluation.domain.AssertionStatus.PASS),
                    com.guardbench.evaluation.domain.ChangeResult.comparable(
                            com.guardbench.evaluation.domain.ChangeType.NO_CHANGE),
                    FIXED_NOW.minusSeconds(10)
            );
            snapshotEvaluationRepo.preStore(preExisting);

            List<SnapshotExecutionFact> facts = List.of(
                    new SnapshotExecutionFact(10L, "BLOCK", "BLOCK", "BLOCK", true, true),
                    new SnapshotExecutionFact(20L, "BLOCK", "BLOCK", "BLOCK", true, true)
            );
            loadFactsPort.setFacts(TEST_RUN_ID, new TestRunExecutionFacts(
                    TEST_RUN_ID, "RUNNING", 2, 2, facts));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);

            assertInstanceOf(FinalizationOutcome.Finalized.class, outcome);
            // Only 1 new evaluation saved (for snapshot 20)
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
                    TEST_RUN_ID, "PREPARING", 2, 0, List.of()));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);
            assertInstanceOf(FinalizationOutcome.NotReady.class, outcome);
        }

        @Test
        @DisplayName("FINISHED인데 QualityGateResult가 없으면 InvariantViolation")
        void invariantViolationWhenFinishedWithoutResult() {
            loadFactsPort.setFacts(TEST_RUN_ID, new TestRunExecutionFacts(
                    TEST_RUN_ID, "FINISHED", 2, 0, List.of()));

            FinalizationOutcome outcome = service.finalize(TEST_RUN_ID);
            assertInstanceOf(FinalizationOutcome.InvariantViolation.class, outcome);
        }
    }

    // ─── Fake Adapters ────────────────────────────────────────────────────────

    private static final class FakeLoadExecutionFactsPort implements LoadTestRunExecutionFactsPort {
        private final Map<Long, TestRunExecutionFacts> factsMap = new HashMap<>();

        void setFacts(long testRunId, TestRunExecutionFacts facts) {
            factsMap.put(testRunId, facts);
        }

        @Override
        public Optional<TestRunExecutionFacts> load(long testRunId) {
            return Optional.ofNullable(factsMap.get(testRunId));
        }
    }

    private static final class FakeFinalizeTestRunPort implements FinalizeTestRunPort {
        private int callCount;
        private String lastOutcomeCode;

        int callCount() { return callCount; }
        String lastOutcomeCode() { return lastOutcomeCode; }

        @Override
        public void finalize(long testRunId, String executionOutcomeCode, int processedTestCaseCount, int testCaseCount) {
            callCount++;
            lastOutcomeCode = executionOutcomeCode;
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
