package com.guardbench.testrun.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.guardbench.testdefinition.domain.TestSuiteId;

public final class TestRun {

    private final TestRunId id;
    private final TestSuiteId testSuiteId;
    private final int testCaseCount;
    private TestRunStatus status;
    private int processedTestCaseCount;
    private TestRunExecutionOutcome executionOutcome;
    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;
    private Instant updatedAt;

    private TestRun(TestRunId id, TestSuiteId testSuiteId, int testCaseCount, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.testSuiteId = Objects.requireNonNull(testSuiteId, "testSuiteId must not be null");
        if (testCaseCount <= 0) {
            throw new IllegalArgumentException("testCaseCount must be positive");
        }
        this.testCaseCount = testCaseCount;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = createdAt;
        this.status = TestRunStatus.QUEUED;
    }

    public static TestRun create(TestRunId id, TestSuiteId testSuiteId, int testCaseCount, Instant createdAt) {
        return new TestRun(id, testSuiteId, testCaseCount, createdAt);
    }

    public TestRunId id() { return id; }

    public TestSuiteId testSuiteId() { return testSuiteId; }

    public TestRunStatus status() { return status; }

    public int testCaseCount() { return testCaseCount; }

    public int processedTestCaseCount() { return processedTestCaseCount; }

    public double progressPercent() {
        return processedTestCaseCount * 100.0 / testCaseCount;
    }

    public TestRunExecutionOutcome executionOutcome() { return executionOutcome; }

    public Instant createdAt() { return createdAt; }

    public Instant startedAt() { return startedAt; }

    public Instant completedAt() { return completedAt; }

    public Instant updatedAt() { return updatedAt; }

    public void startPreparing(Instant at) {
        requireStatus(TestRunStatus.QUEUED);
        this.startedAt = requireAfterCreated(at);
        this.updatedAt = at;
        this.status = TestRunStatus.PREPARING;
    }

    public void startRunning(Instant at) {
        requireStatus(TestRunStatus.PREPARING);
        this.updatedAt = requireAtOrAfterUpdated(at);
        this.status = TestRunStatus.RUNNING;
    }

    public void updateProcessedTestCaseCount(int processedTestCaseCount, Instant at) {
        if (status != TestRunStatus.RUNNING) {
            throw new IllegalStateException("progress can only change while RUNNING");
        }
        if (processedTestCaseCount < this.processedTestCaseCount || processedTestCaseCount > testCaseCount) {
            throw new IllegalArgumentException("processedTestCaseCount must be monotonic and within testCaseCount");
        }
        this.processedTestCaseCount = processedTestCaseCount;
        this.updatedAt = requireAtOrAfterUpdated(at);
    }

    public void finish(Collection<TestExecution> executions, Instant at) {
        Objects.requireNonNull(executions, "executions must not be null");
        requireStatus(TestRunStatus.RUNNING, TestRunStatus.PREPARING);
        Map<TestCaseSnapshotId, Set<TargetType>> targets = executionTargets(executions);
        long processed = targets.values().stream().filter(targetsForSnapshot ->
                targetsForSnapshot.equals(EnumSet.allOf(TargetType.class))).count();
        if (processed != testCaseCount) {
            throw new IllegalArgumentException("all snapshots must have one baseline and one candidate execution");
        }
        this.processedTestCaseCount = (int) processed;
        this.executionOutcome = determineOutcome(executions, testCaseCount);
        this.completedAt = requireAtOrAfterUpdated(at);
        this.updatedAt = at;
        this.status = TestRunStatus.FINISHED;
    }

    public static TestRunExecutionOutcome determineOutcome(Collection<TestExecution> executions, int testCaseCount) {
        Objects.requireNonNull(executions, "executions must not be null");
        if (testCaseCount <= 0 || executions.size() != testCaseCount * 2) {
            throw new IllegalArgumentException("exactly two executions are required per test case");
        }
        executionTargets(executions).values().forEach(targets -> {
            if (!targets.equals(EnumSet.allOf(TargetType.class))) {
                throw new IllegalArgumentException("each snapshot must have one baseline and one candidate execution");
            }
        });
        boolean hasSuccess = executions.stream().anyMatch(execution ->
                execution.status() == TestExecutionResultStatus.SUCCEEDED);
        boolean allSucceeded = executions.stream().allMatch(execution ->
                execution.status() == TestExecutionResultStatus.SUCCEEDED);
        if (allSucceeded) {
            return TestRunExecutionOutcome.COMPLETED;
        }
        return hasSuccess ? TestRunExecutionOutcome.INCOMPLETE : TestRunExecutionOutcome.ERROR;
    }

    private static Map<TestCaseSnapshotId, Set<TargetType>> executionTargets(Collection<TestExecution> executions) {
        Map<TestCaseSnapshotId, Set<TargetType>> targetsBySnapshot = new HashMap<>();
        for (TestExecution execution : executions) {
            targetsBySnapshot.computeIfAbsent(execution.id().snapshotId(), ignored -> EnumSet.noneOf(TargetType.class))
                    .add(execution.id().targetType());
        }
        return targetsBySnapshot;
    }

    private void requireStatus(TestRunStatus... allowed) {
        for (TestRunStatus candidate : allowed) {
            if (status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("invalid TestRun transition from " + status);
    }

    private Instant requireAfterCreated(Instant at) {
        Objects.requireNonNull(at, "time must not be null");
        if (at.isBefore(createdAt)) {
            throw new IllegalArgumentException("time must not precede createdAt");
        }
        return at;
    }

    private Instant requireAtOrAfterUpdated(Instant at) {
        Objects.requireNonNull(at, "time must not be null");
        if (at.isBefore(updatedAt)) {
            throw new IllegalArgumentException("time must not precede updatedAt");
        }
        return at;
    }
}
