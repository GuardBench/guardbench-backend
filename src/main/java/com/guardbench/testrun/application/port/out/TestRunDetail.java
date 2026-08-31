package com.guardbench.testrun.application.port.out;

import java.time.Instant;
import java.util.Objects;

import com.guardbench.testrun.domain.TestRunExecutionOutcome;
import com.guardbench.testrun.domain.TestRunStatus;
import com.guardbench.testrun.domain.EvaluationProfile;

public record TestRunDetail(
        long id,
        long testSuiteId,
        TestRunStatus status,
        int testCaseCount,
        TestRunProgress progress,
        TargetReferenceView target,
        EvaluationProfile evaluationProfile,
        TestRunExecutionOutcome executionOutcome,
        QualityGateView qualityGate,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt) {
    public TestRunDetail(long id, long testSuiteId, TestRunStatus status, int testCaseCount,
                         TestRunProgress progress, TargetReferenceView target,
                         TestRunExecutionOutcome executionOutcome, QualityGateView qualityGate,
                         Instant createdAt, Instant startedAt, Instant completedAt, Instant updatedAt) {
        this(id, testSuiteId, status, testCaseCount, progress, target, null, executionOutcome, qualityGate,
                createdAt, startedAt, completedAt, updatedAt);
    }
    public TestRunDetail {
        if (id <= 0 || testSuiteId <= 0 || testCaseCount <= 0) {
            throw new IllegalArgumentException("TestRun IDs and testCaseCount must be positive");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(progress, "progress must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (progress.processedTestCaseCount() > testCaseCount) {
            throw new IllegalArgumentException("processed count cannot exceed testCaseCount");
        }
    }
}
