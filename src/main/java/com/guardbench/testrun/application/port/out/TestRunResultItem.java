package com.guardbench.testrun.application.port.out;

import java.util.Objects;

import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.Severity;

public record TestRunResultItem(
        long snapshotId,
        long testCaseId,
        String name,
        String input,
        Action expectedAction,
        Severity severity,
        String category,
        TestExecutionView execution,
        String assertionStatusCode,
        String evaluationOutcomeCode) {
    public TestRunResultItem(
            long snapshotId,
            long testCaseId,
            String name,
            String input,
            Action expectedAction,
            Severity severity,
            String category,
            TestExecutionView execution,
            String assertionStatusCode) {
        this(snapshotId, testCaseId, name, input, expectedAction, severity, category,
                execution, assertionStatusCode, null);
    }

    public TestRunResultItem {
        if (snapshotId <= 0 || testCaseId <= 0) {
            throw new IllegalArgumentException("snapshotId and testCaseId must be positive");
        }
        requireNonBlank(name, "name");
        requireNonBlank(input, "input");
        requireNonBlank(category, "category");
        Objects.requireNonNull(expectedAction, "expectedAction must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(execution, "execution must not be null");
        validateCode(assertionStatusCode, "assertionStatusCode", "PASS", "FAIL");
        validateCode(evaluationOutcomeCode, "evaluationOutcomeCode",
                "TRUE_POSITIVE", "TRUE_NEGATIVE", "FALSE_POSITIVE", "FALSE_NEGATIVE");
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void validateCode(String value, String field, String... allowed) {
        if (value == null) {
            return;
        }
        for (String code : allowed) {
            if (code.equals(value)) {
                return;
            }
        }
        throw new IllegalArgumentException("unsupported " + field + "=" + value);
    }
}
