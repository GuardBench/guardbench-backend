package com.guardbench.testrun.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.ExpectedResult;
import com.guardbench.testdefinition.domain.TestSuiteId;

class TestRunTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T00:00:00Z");
    private static final Instant PREPARING_AT = CREATED_AT.plusSeconds(1);
    private static final Instant RUNNING_AT = CREATED_AT.plusSeconds(2);
    private static final Instant FINISHED_AT = CREATED_AT.plusSeconds(3);

    @Test
    @DisplayName("TestRun은 QUEUED에서 PREPARING, RUNNING, FINISHED 순서로만 전이한다")
    void followsApprovedLifecycleTransitions() {
        TestRun testRun = testRun(1);

        testRun.startPreparing(PREPARING_AT);
        testRun.startRunning(RUNNING_AT);
        testRun.finish(successfulExecutions(1), FINISHED_AT);

        assertThat(testRun.status()).isEqualTo(TestRunStatus.FINISHED);
        assertThat(testRun.executionOutcome()).isEqualTo(TestRunExecutionOutcome.COMPLETED);
        assertThat(testRun.processedTestCaseCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("허용되지 않은 TestRun 상태 전이는 예외를 발생시킨다")
    void rejectsInvalidLifecycleTransitions() {
        TestRun testRun = testRun(1);

        assertThatIllegalStateException().isThrownBy(() -> testRun.startRunning(RUNNING_AT));
        testRun.startPreparing(PREPARING_AT);
        assertThatIllegalStateException().isThrownBy(() -> testRun.startPreparing(RUNNING_AT));
    }

    @Test
    @DisplayName("실패와 timeout도 Snapshot 두 실행이 끝나면 진행률에 포함한다")
    void countsFailedAndTimedOutExecutionsAsProcessed() {
        TestRun testRun = testRun(1);
        testRun.startPreparing(PREPARING_AT);
        testRun.startRunning(RUNNING_AT);

        testRun.finish(List.of(
                TestExecution.failed(executionId(1, TargetType.BASELINE), error(), RUNNING_AT, FINISHED_AT),
                TestExecution.timedOut(executionId(1, TargetType.CANDIDATE), error(), RUNNING_AT, FINISHED_AT)),
                FINISHED_AT);

        assertThat(testRun.processedTestCaseCount()).isEqualTo(1);
        assertThat(testRun.progressPercent()).isEqualTo(100.0);
        assertThat(testRun.executionOutcome()).isEqualTo(TestRunExecutionOutcome.ERROR);
    }

    @Test
    @DisplayName("성공과 실패가 섞인 실행은 INCOMPLETE으로 종료한다")
    void finishesIncompleteWhenSomeExecutionsFail() {
        TestRun testRun = testRun(1);
        testRun.startPreparing(PREPARING_AT);
        testRun.startRunning(RUNNING_AT);

        testRun.finish(List.of(
                TestExecution.succeeded(executionId(1, TargetType.BASELINE),
                        new ActualResult(Action.ALLOW), RUNNING_AT, FINISHED_AT),
                TestExecution.failed(executionId(1, TargetType.CANDIDATE), error(), RUNNING_AT, FINISHED_AT)),
                FINISHED_AT);

        assertThat(testRun.executionOutcome()).isEqualTo(TestRunExecutionOutcome.INCOMPLETE);
    }

    @Test
    @DisplayName("같은 Snapshot의 동일 Target 실행 두 개는 Baseline/Candidate 쌍으로 인정하지 않는다")
    void rejectsDuplicateTargetForSnapshot() {
        TestRun testRun = testRun(1);
        testRun.startPreparing(PREPARING_AT);
        testRun.startRunning(RUNNING_AT);
        TestExecution baseline = TestExecution.succeeded(
                executionId(1, TargetType.BASELINE), new ActualResult(Action.ALLOW), RUNNING_AT, FINISHED_AT);

        assertThatIllegalArgumentException().isThrownBy(() -> testRun.finish(List.of(baseline, baseline), FINISHED_AT));
    }

    @Test
    @DisplayName("TestRun은 처리 대상 수가 양수여야 생성된다")
    void requiresPositiveTestCaseCount() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> testRun(0));
    }

    private static TestRun testRun(int testCaseCount) {
        return TestRun.create(new TestRunId(1), new TestSuiteId(1), testCaseCount, CREATED_AT);
    }

    private static List<TestExecution> successfulExecutions(long snapshotId) {
        return List.of(
                TestExecution.succeeded(executionId(snapshotId, TargetType.BASELINE),
                        new ActualResult(Action.ALLOW), RUNNING_AT, FINISHED_AT),
                TestExecution.succeeded(executionId(snapshotId, TargetType.CANDIDATE),
                        new ActualResult(Action.ALLOW), RUNNING_AT, FINISHED_AT));
    }

    private static TestExecutionId executionId(long snapshotId, TargetType targetType) {
        return new TestExecutionId(new TestCaseSnapshotId(snapshotId), targetType);
    }

    private static ExecutionError error() {
        return new ExecutionError("PROVIDER_UNAVAILABLE", "Provider를 사용할 수 없습니다.");
    }
}
