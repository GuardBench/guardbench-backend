package com.guardbench.testrun.domain;

import java.util.Objects;

public record TestCaseSnapshot(
        TestCaseSnapshotId id,
        TestRunId testRunId,
        SourceTestCaseId sourceTestCaseId,
        String name,
        String input,
        ExpectedResult expectedResult,
        Severity severity,
        String category
) {

    public TestCaseSnapshot {
        Objects.requireNonNull(id, "snapshot ID must not be null");
        Objects.requireNonNull(testRunId, "TestRun ID must not be null");
        Objects.requireNonNull(sourceTestCaseId, "source TestCase ID must not be null");
        validateText(name, "name");
        validateText(input, "input");
        Objects.requireNonNull(expectedResult, "expected result must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        validateText(category, "category");
    }

    public static TestCaseSnapshot of(
            TestCaseSnapshotId id,
            TestRunId testRunId,
            SourceTestCaseId sourceTestCaseId,
            String name,
            String input,
            ExpectedResult expectedResult,
            Severity severity,
            String category
    ) {
        return new TestCaseSnapshot(id, testRunId, sourceTestCaseId, name, input, expectedResult, severity, category);
    }

    private static void validateText(String value, String field) {
        if (isContractBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static boolean isContractBlank(String value) {
        return value == null || value.codePoints().allMatch(TestCaseSnapshot::isContractWhitespace);
    }

    private static boolean isContractWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0xFEFF;
    }
}
