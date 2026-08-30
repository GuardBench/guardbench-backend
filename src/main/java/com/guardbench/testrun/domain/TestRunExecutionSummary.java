package com.guardbench.testrun.domain;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class TestRunExecutionSummary {

    private final int testCaseCount;
    private final int processedTestCaseCount;
    private final int succeededExecutionCount;

    private TestRunExecutionSummary(int testCaseCount, int processedTestCaseCount, int succeededExecutionCount) {
        this.testCaseCount = testCaseCount;
        this.processedTestCaseCount = processedTestCaseCount;
        this.succeededExecutionCount = succeededExecutionCount;
    }

    public static TestRunExecutionSummary from(Collection<SnapshotExecutionStatus> executionStatuses) {
        Objects.requireNonNull(executionStatuses, "execution statuses must not be null");
        if (executionStatuses.isEmpty()) {
            throw new IllegalArgumentException("execution statuses must not be empty");
        }

        Set<TestCaseSnapshotId> snapshotIds = new HashSet<>();
        int processedCount = 0;
        int succeededCount = 0;
        for (SnapshotExecutionStatus executionStatus : executionStatuses) {
            Objects.requireNonNull(executionStatus, "execution status must not be null");
            if (!snapshotIds.add(executionStatus.snapshotId())) {
                throw new IllegalArgumentException("execution statuses must contain each snapshot once");
            }
            if (executionStatus.isProcessed()) {
                processedCount++;
            }
            if (executionStatus.succeeded()) {
                succeededCount++;
            }
        }

        return new TestRunExecutionSummary(executionStatuses.size(), processedCount, succeededCount);
    }

    public int testCaseCount() {
        return testCaseCount;
    }

    public int processedTestCaseCount() {
        return processedTestCaseCount;
    }

    public TestRunExecutionOutcome outcome() {
        if (processedTestCaseCount != testCaseCount) {
            throw new IllegalStateException("execution outcome requires every snapshot to be processed");
        }
        if (succeededExecutionCount == testCaseCount) {
            return TestRunExecutionOutcome.COMPLETED;
        }
        if (succeededExecutionCount > 0) {
            return TestRunExecutionOutcome.INCOMPLETE;
        }
        return TestRunExecutionOutcome.ERROR;
    }
}
