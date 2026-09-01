package com.guardbench.testrun.application.port.out;

import java.util.Objects;

import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.Severity;

/** 저장된 TestCaseSnapshot과 Evaluator verdict의 consumer-owned projection이다. */
public record TestRunRegressionSnapshot(
        long snapshotId,
        long sourceTestCaseId,
        String name,
        String input,
        Action expectedAction,
        Severity severity,
        String category,
        Action evaluatorVerdict) {

    public TestRunRegressionSnapshot {
        if (snapshotId <= 0 || sourceTestCaseId <= 0) {
            throw new IllegalArgumentException("Snapshot and TestCase IDs must be positive");
        }
        requireNonBlank(name, "name");
        requireNonBlank(input, "input");
        requireNonBlank(category, "category");
        Objects.requireNonNull(expectedAction, "expectedAction must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
    }

    public boolean hasSameDefinitionAs(TestRunRegressionSnapshot other) {
        return sourceTestCaseId == other.sourceTestCaseId
                && name.equals(other.name)
                && input.equals(other.input)
                && expectedAction == other.expectedAction
                && severity == other.severity
                && category.equals(other.category);
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
