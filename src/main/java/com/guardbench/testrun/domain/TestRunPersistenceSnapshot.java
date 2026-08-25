package com.guardbench.testrun.domain;

import java.util.Objects;

/**
 * Immutable state exported by {@link TestRun} for persistence mapping.
 * This value contains no persistence-framework type.
 */
public record TestRunPersistenceSnapshot(
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
    public TestRunPersistenceSnapshot {
        Objects.requireNonNull(id, "TestRun ID must not be null");
        Objects.requireNonNull(sourceTestSuiteId, "source TestSuite ID must not be null");
        Objects.requireNonNull(baselineTarget, "baseline target must not be null");
        Objects.requireNonNull(candidateTarget, "candidate target must not be null");
        if (testCaseCount <= 0) {
            throw new IllegalArgumentException("test case count must be positive");
        }
        if (processedTestCaseCount < 0 || processedTestCaseCount > testCaseCount) {
            throw new IllegalArgumentException("processed TestCase count must be between zero and total count");
        }
        Objects.requireNonNull(status, "TestRun status must not be null");
        Objects.requireNonNull(timeline, "TestRun timeline must not be null");
    }
}
