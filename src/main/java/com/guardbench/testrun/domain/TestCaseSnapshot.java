package com.guardbench.testrun.domain;

import java.util.Objects;

import com.guardbench.testdefinition.domain.ExpectedResult;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testdefinition.domain.TestCaseId;

public final class TestCaseSnapshot {

    private final TestCaseSnapshotId id;
    private final TestRunId testRunId;
    private final TestCaseId sourceTestCaseId;
    private final String name;
    private final String input;
    private final ExpectedResult expectedResult;
    private final Severity severity;
    private final String category;

    public TestCaseSnapshot(
            TestCaseSnapshotId id,
            TestRunId testRunId,
            TestCaseId sourceTestCaseId,
            String name,
            String input,
            ExpectedResult expectedResult,
            Severity severity,
            String category) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.testRunId = Objects.requireNonNull(testRunId, "testRunId must not be null");
        this.sourceTestCaseId = Objects.requireNonNull(sourceTestCaseId, "sourceTestCaseId must not be null");
        this.name = requireText(name, "name");
        this.input = requireText(input, "input");
        this.expectedResult = Objects.requireNonNull(expectedResult, "expectedResult must not be null");
        this.severity = Objects.requireNonNull(severity, "severity must not be null");
        this.category = requireText(category, "category");
    }

    public TestCaseSnapshotId id() { return id; }

    public TestRunId testRunId() { return testRunId; }

    public TestCaseId sourceTestCaseId() { return sourceTestCaseId; }

    public String name() { return name; }

    public String input() { return input; }

    public ExpectedResult expectedResult() { return expectedResult; }

    public Severity severity() { return severity; }

    public String category() { return category; }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
