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
            assertEquals(CREATED_AT.plusSeconds(1), testRun.startedAt());
            assertEquals(CREATED_AT.plusSeconds(4), testRun.completedAt());
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
            assertEquals(CREATED_AT.plusSeconds(2), testRun.completedAt());
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
