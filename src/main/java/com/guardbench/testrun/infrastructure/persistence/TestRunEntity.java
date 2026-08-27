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
    String baselineGuardrailId;
    String baselineVersion;
    String candidateGuardrailId;
    String candidateRequestedSource;
    String candidateResolvedVersion;
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
            String baselineGuardrailId,
            String baselineVersion,
            String candidateGuardrailId,
            String candidateRequestedSource,
            String candidateResolvedVersion,
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
        this.baselineGuardrailId = baselineGuardrailId;
        this.baselineVersion = baselineVersion;
        this.candidateGuardrailId = candidateGuardrailId;
        this.candidateRequestedSource = candidateRequestedSource;
        this.candidateResolvedVersion = candidateResolvedVersion;
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
            String baselineGuardrailId,
            String baselineVersion,
            String candidateGuardrailId,
            String candidateRequestedSource,
            String candidateResolvedVersion,
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
                baselineGuardrailId,
                baselineVersion,
                candidateGuardrailId,
                candidateRequestedSource,
                candidateResolvedVersion,
                executionOutcome,
                createdAt,
                startedAt,
                completedAt,
                updatedAt
        );
    }
}
