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

    public static TestRunExecutionSummary from(Collection<SnapshotExecutionPair> executionPairs) {
        Objects.requireNonNull(executionPairs, "execution pairs must not be null");
        if (executionPairs.isEmpty()) {
            throw new IllegalArgumentException("execution pairs must not be empty");
        }

        Set<TestCaseSnapshotId> snapshotIds = new HashSet<>();
        int processedCount = 0;
        int succeededCount = 0;
        for (SnapshotExecutionPair executionPair : executionPairs) {
            Objects.requireNonNull(executionPair, "execution pair must not be null");
            if (!snapshotIds.add(executionPair.snapshotId())) {
                throw new IllegalArgumentException("execution pairs must contain each snapshot once");
            }
            if (executionPair.isProcessed()) {
                processedCount++;
            }
            succeededCount += executionPair.succeededExecutionCount();
        }

        return new TestRunExecutionSummary(executionPairs.size(), processedCount, succeededCount);
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
        if (succeededExecutionCount == testCaseCount * 2) {
            return TestRunExecutionOutcome.COMPLETED;
        }
        if (succeededExecutionCount > 0) {
            return TestRunExecutionOutcome.INCOMPLETE;
        }
        return TestRunExecutionOutcome.ERROR;
    }
}
