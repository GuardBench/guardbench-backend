package com.guardbench.testrun.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TestRunTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T00:00:00Z");

    @Nested
    @DisplayName("수명주기")
    class Lifecycle {

        @Test
        @DisplayName("QUEUED TestRun은 PREPARING과 RUNNING을 거쳐 COMPLETED로 종료한다")
        void queuedTestRunTransitionsToFinishedCompleted() {
            TestRun testRun = queuedTestRun(2);
            TestRunExecutionSummary executionSummary = summary(
                    succeededPair(1),
                    succeededPair(2)
            );

            testRun.beginPreparing(CREATED_AT.plusSeconds(1));
            testRun.beginRunning("5", CREATED_AT.plusSeconds(2));
            testRun.updateProgress(executionSummary, CREATED_AT.plusSeconds(3));
            testRun.finish(executionSummary, CREATED_AT.plusSeconds(4));

            assertEquals(TestRunStatus.FINISHED, testRun.status());
            assertEquals(TestRunExecutionOutcome.COMPLETED, testRun.executionOutcome());
            assertEquals(2, testRun.processedTestCaseCount());
            assertEquals("5", testRun.candidateTarget().resolvedVersion());
            assertEquals(CREATED_AT.plusSeconds(1), testRun.timeline().startedAt());
            assertEquals(CREATED_AT.plusSeconds(4), testRun.timeline().completedAt());
        }

        @Test
        @DisplayName("PREPARING TestRun은 대상 준비 실패 시 ERROR로 종료한다")
        void preparingTestRunFailsWithErrorWhenTargetPreparationFails() {
            TestRun testRun = queuedTestRun(2);

            testRun.beginPreparing(CREATED_AT.plusSeconds(1));
            testRun.failPreparation(CREATED_AT.plusSeconds(2));

            assertEquals(TestRunStatus.FINISHED, testRun.status());
            assertEquals(TestRunExecutionOutcome.ERROR, testRun.executionOutcome());
            assertEquals(2, testRun.processedTestCaseCount());
            assertEquals(CREATED_AT.plusSeconds(2), testRun.timeline().completedAt());
        }

        @Test
        @DisplayName("허용되지 않은 상태 전이는 거부한다")
        void rejectsInvalidLifecycleTransitions() {
            TestRun testRun = queuedTestRun(1);

            assertThrows(IllegalStateException.class, () -> testRun.beginRunning("5", CREATED_AT));
            assertThrows(IllegalStateException.class, () -> testRun.finish(summary(succeededPair(1)), CREATED_AT));

            testRun.beginPreparing(CREATED_AT.plusSeconds(1));

            assertThrows(IllegalStateException.class, () -> testRun.updateProgress(summary(succeededPair(1)), CREATED_AT));
            assertThrows(IllegalStateException.class, () -> testRun.beginPreparing(CREATED_AT));
        }

        @Test
        @DisplayName("Candidate 확정 version 없이는 RUNNING으로 전이하지 않는다")
        void rejectsRunningTransitionWithoutResolvedCandidateVersion() {
            TestRun testRun = queuedTestRun(1);
            testRun.beginPreparing(CREATED_AT.plusSeconds(1));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> testRun.beginRunning(null, CREATED_AT.plusSeconds(2))
            );

            assertEquals(TestRunStatus.PREPARING, testRun.status());
            assertNull(testRun.candidateTarget().resolvedVersion());
        }

        @Test
        @DisplayName("완료 시각이 시작 시각보다 앞서면 FINISHED로 전이하지 않는다")
        void rejectsFinishWithCompletedTimeBeforeStartedTime() {
            TestRun testRun = queuedTestRun(1);
            testRun.beginPreparing(CREATED_AT.plusSeconds(10));
            testRun.beginRunning("5", CREATED_AT.plusSeconds(11));

            assertThrows(
                    IllegalArgumentException.class,
                    () -> testRun.finish(summary(succeededPair(1)), CREATED_AT.plusSeconds(9))
            );

            assertEquals(TestRunStatus.RUNNING, testRun.status());
            assertNull(testRun.executionOutcome());
            assertNull(testRun.timeline().completedAt());
        }
    }

    @Test
    @DisplayName("Baseline과 Candidate는 동일한 Guardrail ID가 아니면 생성할 수 없다")
    void rejectsDifferentBaselineAndCandidateGuardrails() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TestRun.queue(
                        new TestRunId(1),
                        new SourceTestSuiteId(10),
                        new BaselineTarget("baseline-guardrail", "4"),
                        new CandidateTarget("candidate-guardrail", CandidateSource.DRAFT, null),
                        1,
                        CREATED_AT
                )
        );
    }

    @Test
    @DisplayName("persistence snapshot은 저장에 필요한 TestRun 상태를 하나의 불변 값으로 제공한다")
    void persistenceSnapshotExposesCompleteAggregateState() {
        TestRun testRun = queuedTestRun(2);
        testRun.beginPreparing(CREATED_AT.plusSeconds(1));
        testRun.beginRunning("5", CREATED_AT.plusSeconds(2));
        testRun.updateProgress(summary(succeededPair(1), succeededPair(2)), CREATED_AT.plusSeconds(3));

        TestRunPersistenceSnapshot snapshot = testRun.persistenceSnapshot();

        assertEquals(new TestRunId(1), snapshot.id());
        assertEquals(new SourceTestSuiteId(10), snapshot.sourceTestSuiteId());
        assertEquals("4", snapshot.baselineTarget().version());
        assertEquals("5", snapshot.candidateTarget().resolvedVersion());
        assertEquals(2, snapshot.testCaseCount());
        assertEquals(2, snapshot.processedTestCaseCount());
        assertEquals(TestRunStatus.RUNNING, snapshot.status());
        assertNull(snapshot.executionOutcome());
        assertEquals(CREATED_AT.plusSeconds(3), snapshot.timeline().updatedAt());
    }

    private static TestRun queuedTestRun(int testCaseCount) {
        return TestRun.queue(
                new TestRunId(1),
                new SourceTestSuiteId(10),
                new BaselineTarget("guardrail-123", "4"),
                new CandidateTarget("guardrail-123", CandidateSource.DRAFT, null),
                testCaseCount,
                CREATED_AT
        );
    }

    private static TestRunExecutionSummary summary(SnapshotExecutionPair... executionPairs) {
        return TestRunExecutionSummary.from(List.of(executionPairs));
    }

    private static SnapshotExecutionPair succeededPair(long snapshotId) {
        return new SnapshotExecutionPair(
                new TestCaseSnapshotId(snapshotId),
                TestExecutionStatus.SUCCEEDED,
                TestExecutionStatus.SUCCEEDED
        );
    }
}
