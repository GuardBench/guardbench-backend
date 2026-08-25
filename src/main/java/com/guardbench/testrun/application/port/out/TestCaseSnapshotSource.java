package com.guardbench.testrun.application.port.out;

public record TestCaseSnapshotSource(
        long sourceTestSuiteId,
        long sourceTestCaseId,
        String name,
        String input,
        String expectedActionCode,
        String severityCode,
        String category
) {

    public TestCaseSnapshotSource {
        if (sourceTestSuiteId <= 0 || sourceTestCaseId <= 0) {
            throw new IllegalArgumentException("source IDs must be positive");
        }
        validateText(name, "name");
        validateText(input, "input");
        validateText(expectedActionCode, "expected action code");
        validateText(severityCode, "severity code");
        validateText(category, "category");
    }

    private static void validateText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
