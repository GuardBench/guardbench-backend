package com.guardbench.testrun.domain;

import java.time.Instant;
import java.util.Objects;

public final class TestRun {

    private final TestRunId id;
    private final SourceTestSuiteId sourceTestSuiteId;
    private final BaselineTarget baselineTarget;
    private CandidateTarget candidateTarget;
    private final int testCaseCount;
    private int processedTestCaseCount;
    private TestRunStatus status;
    private TestRunExecutionOutcome executionOutcome;
    private TestRunTimeline timeline;

    private TestRun(
            TestRunId id,
            SourceTestSuiteId sourceTestSuiteId,
            BaselineTarget baselineTarget,
            CandidateTarget candidateTarget,
            int testCaseCount,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "TestRun ID must not be null");
        this.sourceTestSuiteId = Objects.requireNonNull(sourceTestSuiteId, "source TestSuite ID must not be null");
        this.baselineTarget = Objects.requireNonNull(baselineTarget, "baseline target must not be null");
        this.candidateTarget = Objects.requireNonNull(candidateTarget, "candidate target must not be null");
        if (!baselineTarget.guardrailId().equals(candidateTarget.guardrailId())) {
            throw new IllegalArgumentException("baseline and candidate must use the same guardrail ID");
        }
        if (testCaseCount <= 0) {
            throw new IllegalArgumentException("test case count must be positive");
        }
        this.testCaseCount = testCaseCount;
        this.timeline = TestRunTimeline.created(createdAt);
        this.status = TestRunStatus.QUEUED;
    }

    public static TestRun queue(
            TestRunId id,
            SourceTestSuiteId sourceTestSuiteId,
            BaselineTarget baselineTarget,
            CandidateTarget candidateTarget,
            int testCaseCount,
            Instant createdAt
    ) {
        return new TestRun(id, sourceTestSuiteId, baselineTarget, candidateTarget, testCaseCount, createdAt);
    }

    public static TestRun rehydrate(
            TestRunId id,
            SourceTestSuiteId sourceTestSuiteId,
            BaselineTarget baselineTarget,
            CandidateTarget candidateTarget,
            int testCaseCount,
            int processedTestCaseCount,
            TestRunStatus status,
            TestRunExecutionOutcome executionOutcome,
            TestRunTimeline timeline
    ) {
        TestRun testRun = new TestRun(
                id,
                sourceTestSuiteId,
                baselineTarget,
                candidateTarget,
                testCaseCount,
                timeline.createdAt()
        );
        if (processedTestCaseCount < 0 || processedTestCaseCount > testCaseCount) {
            throw new IllegalArgumentException("processed TestCase count must be between zero and total count");
        }
        testRun.processedTestCaseCount = processedTestCaseCount;
        testRun.status = Objects.requireNonNull(status, "TestRun status must not be null");
        testRun.executionOutcome = executionOutcome;
        testRun.timeline = Objects.requireNonNull(timeline, "TestRun timeline must not be null");
        return testRun;
    }

    public void beginPreparing(Instant preparedAt) {
        requireStatus(TestRunStatus.QUEUED, "begin preparation");
        timeline = timeline.start(preparedAt);
        status = TestRunStatus.PREPARING;
    }

    public void beginRunning(String resolvedCandidateVersion, Instant runningAt) {
        requireStatus(TestRunStatus.PREPARING, "begin execution");
        candidateTarget = candidateTarget.resolve(resolvedCandidateVersion);
        timeline = timeline.touch(runningAt);
        status = TestRunStatus.RUNNING;
    }

    public void updateProgress(TestRunExecutionSummary executionSummary, Instant updatedAt) {
        requireStatus(TestRunStatus.RUNNING, "update progress");
        validateSummary(executionSummary);
        processedTestCaseCount = executionSummary.processedTestCaseCount();
        timeline = timeline.touch(updatedAt);
    }

    public void finish(TestRunExecutionSummary executionSummary, Instant completedAt) {
        requireStatus(TestRunStatus.RUNNING, "finish");
        validateSummary(executionSummary);
        TestRunExecutionOutcome outcome = executionSummary.outcome();
        timeline = timeline.complete(completedAt);
        processedTestCaseCount = executionSummary.processedTestCaseCount();
        executionOutcome = outcome;
        status = TestRunStatus.FINISHED;
    }

    public void failPreparation(Instant completedAt) {
        requireStatus(TestRunStatus.PREPARING, "fail preparation");
        timeline = timeline.complete(completedAt);
        processedTestCaseCount = testCaseCount;
        executionOutcome = TestRunExecutionOutcome.ERROR;
        status = TestRunStatus.FINISHED;
    }

    public TestRunId id() {
        return id;
    }

    /**
     * Exports immutable aggregate state for the Infrastructure persistence mapper.
     */
    public TestRunPersistenceSnapshot persistenceSnapshot() {
        return new TestRunPersistenceSnapshot(
                id,
                sourceTestSuiteId,
                baselineTarget,
                candidateTarget,
                testCaseCount,
                processedTestCaseCount,
                status,
                executionOutcome,
                timeline
        );
    }

    SourceTestSuiteId sourceTestSuiteId() {
        return sourceTestSuiteId;
    }

    BaselineTarget baselineTarget() {
        return baselineTarget;
    }

    CandidateTarget candidateTarget() {
        return candidateTarget;
    }

    int testCaseCount() {
        return testCaseCount;
    }

    int processedTestCaseCount() {
        return processedTestCaseCount;
    }

    public TestRunStatus status() {
        return status;
    }

    TestRunExecutionOutcome executionOutcome() {
        return executionOutcome;
    }

    TestRunTimeline timeline() {
        return timeline;
    }

    private void validateSummary(TestRunExecutionSummary executionSummary) {
        Objects.requireNonNull(executionSummary, "execution summary must not be null");
        if (executionSummary.testCaseCount() != testCaseCount) {
            throw new IllegalArgumentException("execution summary must include every TestCaseSnapshot");
        }
    }

    private void requireStatus(TestRunStatus expectedStatus, String operation) {
        if (status != expectedStatus) {
            throw new IllegalStateException("cannot " + operation + " when TestRun status is " + status);
        }
    }
}
