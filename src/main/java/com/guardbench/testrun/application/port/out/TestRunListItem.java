package com.guardbench.testrun.application.port.out;

import java.time.Instant;
import java.util.Objects;

import com.guardbench.testrun.domain.TestRunExecutionOutcome;
import com.guardbench.testrun.domain.TestRunStatus;

public record TestRunListItem(
        long id,
        long testSuiteId,
        TestRunStatus status,
        int testCaseCount,
        TestRunProgress progress,
        TestRunExecutionOutcome executionOutcome,
        String qualityGateStatusCode,
        QualityGateMetricsView qualityGateMetrics,
        Instant createdAt,
        Instant startedAt,
        Instant completedAt,
        Instant updatedAt) {
    public TestRunListItem {
        if (id <= 0 || testSuiteId <= 0 || testCaseCount <= 0) {
            throw new IllegalArgumentException("TestRun IDs and testCaseCount must be positive");
        }
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(progress, "progress must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (progress.processedTestCaseCount() > testCaseCount) {
            throw new IllegalArgumentException("processed count cannot exceed testCaseCount");
        }
        if (qualityGateStatusCode != null) {
            TestRunListCriteria.validateQualityGateStatusCode(qualityGateStatusCode);
        }
        if (qualityGateStatusCode == null && qualityGateMetrics != null) {
            throw new IllegalArgumentException("undecided Quality Gate must not have metrics");
        }
        if ("NOT_EVALUATED".equals(qualityGateStatusCode) && qualityGateMetrics != null) {
            throw new IllegalArgumentException("NOT_EVALUATED Quality Gate must not have metrics");
        }
        if (("PASS".equals(qualityGateStatusCode) || "FAIL".equals(qualityGateStatusCode))
                && qualityGateMetrics == null) {
            throw new IllegalArgumentException("evaluated Quality Gate must have metrics");
        }
    }
}
