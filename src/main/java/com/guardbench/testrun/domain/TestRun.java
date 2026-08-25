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
    private final Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;

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
        this.createdAt = Objects.requireNonNull(createdAt, "created time must not be null");
        this.updatedAt = createdAt;
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

    public void beginPreparing(Instant preparedAt) {
        requireStatus(TestRunStatus.QUEUED, "begin preparation");
        startedAt = requireTime(preparedAt);
        updatedAt = preparedAt;
        status = TestRunStatus.PREPARING;
    }

    public void beginRunning(String resolvedCandidateVersion, Instant startedAt) {
        requireStatus(TestRunStatus.PREPARING, "begin execution");
        candidateTarget = candidateTarget.resolve(resolvedCandidateVersion);
        this.updatedAt = requireTime(startedAt);
        status = TestRunStatus.RUNNING;
    }

    public void updateProgress(TestRunExecutionSummary executionSummary, Instant updatedAt) {
        requireStatus(TestRunStatus.RUNNING, "update progress");
        validateSummary(executionSummary);
        processedTestCaseCount = executionSummary.processedTestCaseCount();
        this.updatedAt = requireTime(updatedAt);
    }

    public void finish(TestRunExecutionSummary executionSummary, Instant completedAt) {
        requireStatus(TestRunStatus.RUNNING, "finish");
        validateSummary(executionSummary);
        TestRunExecutionOutcome outcome = executionSummary.outcome();
        processedTestCaseCount = executionSummary.processedTestCaseCount();
        executionOutcome = outcome;
        this.completedAt = requireTime(completedAt);
        updatedAt = completedAt;
        status = TestRunStatus.FINISHED;
    }

    public void failPreparation(Instant completedAt) {
        requireStatus(TestRunStatus.PREPARING, "fail preparation");
        processedTestCaseCount = testCaseCount;
        executionOutcome = TestRunExecutionOutcome.ERROR;
        this.completedAt = requireTime(completedAt);
        updatedAt = completedAt;
        status = TestRunStatus.FINISHED;
    }

    public TestRunId id() {
        return id;
    }

    public SourceTestSuiteId sourceTestSuiteId() {
        return sourceTestSuiteId;
    }

    public BaselineTarget baselineTarget() {
        return baselineTarget;
    }

    public CandidateTarget candidateTarget() {
        return candidateTarget;
    }

    public int testCaseCount() {
        return testCaseCount;
    }

    public int processedTestCaseCount() {
        return processedTestCaseCount;
    }

    public TestRunStatus status() {
        return status;
    }

    public TestRunExecutionOutcome executionOutcome() {
        return executionOutcome;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public Instant updatedAt() {
        return updatedAt;
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

    private static Instant requireTime(Instant time) {
        return Objects.requireNonNull(time, "time must not be null");
    }
}
