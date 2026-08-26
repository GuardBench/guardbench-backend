package com.guardbench.testdefinition.application;

import java.time.Instant;

import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testdefinition.domain.TestCase;

public record TestCaseDetail(
        long id,
        long testSuiteId,
        String name,
        String input,
        Action expectedAction,
        Severity severity,
        String category,
        Instant createdAt,
        Instant updatedAt) {

    public static TestCaseDetail from(TestCase testCase) {
        return new TestCaseDetail(
                testCase.id().value(),
                testCase.testSuiteId().value(),
                testCase.name(),
                testCase.input(),
                testCase.expectedResult().action(),
                testCase.severity(),
                testCase.category(),
                testCase.createdAt(),
                testCase.updatedAt());
    }
}
