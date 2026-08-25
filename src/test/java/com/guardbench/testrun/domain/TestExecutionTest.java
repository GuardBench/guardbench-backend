package com.guardbench.testrun.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestExecutionTest {

    @Test
    @DisplayName("SUCCEEDED 실행은 ActualResult만 가진다")
    void succeededExecutionContainsOnlyActualResult() {
        TestExecution execution = TestExecution.succeeded(executionId(TargetType.BASELINE), new ActualResult(Action.ALLOW));

        assertEquals(TestExecutionStatus.SUCCEEDED, execution.status());
        assertEquals(new ActualResult(Action.ALLOW), execution.actualResult());
        assertNull(execution.error());
    }

    @Test
    @DisplayName("FAILED 실행은 허용된 오류 code와 외부 공개용으로 가공된 detail만 가진다")
    void failedExecutionContainsOnlySafeError() {
        TestExecutionError error = new TestExecutionError(
                TestExecutionErrorCode.PROVIDER_UNAVAILABLE,
                "Provider is temporarily unavailable."
        );

        TestExecution execution = TestExecution.failed(executionId(TargetType.CANDIDATE), error);

        assertEquals(TestExecutionStatus.FAILED, execution.status());
        assertNull(execution.actualResult());
        assertEquals(error, execution.error());
    }

    @Test
    @DisplayName("TIMED_OUT 실행은 PROVIDER_TIMEOUT 오류를 요구한다")
    void timedOutExecutionRequiresProviderTimeoutError() {
        TestExecutionError wrongError = new TestExecutionError(
                TestExecutionErrorCode.PROVIDER_UNAVAILABLE,
                "Provider is temporarily unavailable."
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> TestExecution.timedOut(executionId(TargetType.CANDIDATE), wrongError)
        );
    }

    @Test
    @DisplayName("NOT_STARTED 실행은 ActualResult와 오류를 모두 가지지 않는다")
    void notStartedExecutionContainsNeitherResultNorError() {
        TestExecution execution = TestExecution.notStarted(executionId(TargetType.BASELINE));

        assertEquals(TestExecutionStatus.NOT_STARTED, execution.status());
        assertNull(execution.actualResult());
        assertNull(execution.error());
    }

    @Test
    @DisplayName("Baseline과 Candidate 실행은 같은 Snapshot에서 다른 식별자를 가진다")
    void baselineAndCandidateUseDifferentIdsForSameSnapshot() {
        TestExecutionId baselineId = executionId(TargetType.BASELINE);
        TestExecutionId candidateId = executionId(TargetType.CANDIDATE);

        assertEquals(baselineId.snapshotId(), candidateId.snapshotId());
        assertNotEquals(baselineId, candidateId);
    }

    private static TestExecutionId executionId(TargetType targetType) {
        return new TestExecutionId(new TestCaseSnapshotId(1), targetType);
    }
}
