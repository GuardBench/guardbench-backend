package com.guardbench.testrun.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "test_case_snapshot")
class TestCaseSnapshotEntity {
    @Id Long id;
    Long testRunId;
    Long sourceTestCaseId;
    String name;
    String input;
    String expectedAction;
    String severity;
    String category;
    Instant createdAt;

    protected TestCaseSnapshotEntity() {
    }

    private TestCaseSnapshotEntity(
            Long id,
            Long testRunId,
            Long sourceTestCaseId,
            String name,
            String input,
            String expectedAction,
            String severity,
            String category,
            Instant createdAt
    ) {
        this.id = id;
        this.testRunId = testRunId;
        this.sourceTestCaseId = sourceTestCaseId;
        this.name = name;
        this.input = input;
        this.expectedAction = expectedAction;
        this.severity = severity;
        this.category = category;
        this.createdAt = createdAt;
    }

    static TestCaseSnapshotEntity of(
            Long id,
            Long testRunId,
            Long sourceTestCaseId,
            String name,
            String input,
            String expectedAction,
            String severity,
            String category,
            Instant createdAt
    ) {
        return new TestCaseSnapshotEntity(
                id,
                testRunId,
                sourceTestCaseId,
                name,
                input,
                expectedAction,
                severity,
                category,
                createdAt
        );
    }
}
