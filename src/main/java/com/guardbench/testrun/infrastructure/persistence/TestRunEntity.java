package com.guardbench.testrun.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_run")
class TestRunEntity {
    @Id Long id;
    Long testSuiteId;
    String status;
    int testCaseCount;
    int processedTestCaseCount;
    String targetReferenceId;
    String evaluatorReferenceId;
    double assertionPassRateThreshold;
    double executionSuccessRateThreshold;
    String executionOutcome;
    Instant createdAt;
    Instant startedAt;
    Instant completedAt;
    Instant updatedAt;

    protected TestRunEntity() {
    }

    private TestRunEntity(
            Long id,
            Long testSuiteId,
            String status,
            int testCaseCount,
            int processedTestCaseCount,
            String targetReferenceId,
            String evaluatorReferenceId,
            double assertionPassRateThreshold,
            double executionSuccessRateThreshold,
            String executionOutcome,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.testSuiteId = testSuiteId;
        this.status = status;
        this.testCaseCount = testCaseCount;
        this.processedTestCaseCount = processedTestCaseCount;
        this.targetReferenceId = targetReferenceId;
        this.evaluatorReferenceId = evaluatorReferenceId;
        this.assertionPassRateThreshold = assertionPassRateThreshold;
        this.executionSuccessRateThreshold = executionSuccessRateThreshold;
        this.executionOutcome = executionOutcome;
        this.createdAt = createdAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.updatedAt = updatedAt;
    }

    static TestRunEntity of(
            Long id,
            Long testSuiteId,
            String status,
            int testCaseCount,
            int processedTestCaseCount,
            String targetReferenceId,
            String evaluatorReferenceId,
            double assertionPassRateThreshold,
            double executionSuccessRateThreshold,
            String executionOutcome,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant updatedAt
    ) {
        return new TestRunEntity(
                id,
                testSuiteId,
                status,
                testCaseCount,
                processedTestCaseCount,
                targetReferenceId,
                evaluatorReferenceId,
                assertionPassRateThreshold,
                executionSuccessRateThreshold,
                executionOutcome,
                createdAt,
                startedAt,
                completedAt,
                updatedAt
        );
    }
}
