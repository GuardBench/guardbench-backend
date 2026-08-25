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
}
