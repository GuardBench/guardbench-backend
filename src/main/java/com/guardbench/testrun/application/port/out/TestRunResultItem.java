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
        TestExecutionView baselineExecution,
        TestExecutionView candidateExecution,
        String assertionStatusCode,
        String comparabilityStatusCode,
        String changeTypeCode) {
    public TestRunResultItem {
        if (snapshotId <= 0 || testCaseId <= 0) {
            throw new IllegalArgumentException("snapshotId and testCaseId must be positive");
        }
        requireNonBlank(name, "name");
        requireNonBlank(input, "input");
        requireNonBlank(category, "category");
        Objects.requireNonNull(expectedAction, "expectedAction must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(baselineExecution, "baselineExecution must not be null");
        Objects.requireNonNull(candidateExecution, "candidateExecution must not be null");
        validateCode(assertionStatusCode, "assertionStatusCode", "PASS", "FAIL");
        validateCode(comparabilityStatusCode, "comparabilityStatusCode", "COMPARABLE", "NOT_COMPARABLE");
        validateCode(changeTypeCode, "changeTypeCode", "NO_CHANGE", "SECURITY_REGRESSION",
                "USABILITY_REGRESSION", "IMPROVEMENT", "POLICY_BEHAVIOR_CHANGED");
        if (comparabilityStatusCode == null && changeTypeCode != null) {
            throw new IllegalArgumentException("changeTypeCode requires comparabilityStatusCode");
        }
        if ("NOT_COMPARABLE".equals(comparabilityStatusCode) && changeTypeCode != null) {
            throw new IllegalArgumentException("NOT_COMPARABLE result cannot have changeTypeCode");
        }
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
