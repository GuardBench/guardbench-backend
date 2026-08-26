package com.guardbench.testdefinition.application;

import java.util.List;
import java.util.Objects;

public record TestSuiteCreateCommand(
        String name,
        String description,
        List<TestCaseCreateCommand> testCases) {

    public TestSuiteCreateCommand {
        Objects.requireNonNull(testCases, "testCases must not be null");
        testCases = List.copyOf(testCases);
    }
}
