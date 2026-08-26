package com.guardbench.testdefinition.presentation.dto;

import java.time.Instant;

import com.guardbench.testdefinition.application.query.TestCaseSummary;

public record TestCaseListItemRes(
        long id,
        String name,
        String input,
        String expectedAction,
        String severity,
        String category,
        Instant createdAt,
        Instant updatedAt) {

    public static TestCaseListItemRes from(TestCaseSummary summary) {
        return new TestCaseListItemRes(
                summary.id(), summary.name(), summary.input(), summary.expectedAction().name(),
                summary.severity().name(), summary.category(), summary.createdAt(), summary.updatedAt());
    }
}
