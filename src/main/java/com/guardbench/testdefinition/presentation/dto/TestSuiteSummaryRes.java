package com.guardbench.testdefinition.presentation.dto;

import java.time.Instant;

import com.guardbench.testdefinition.application.query.TestSuiteSummary;

public record TestSuiteSummaryRes(
        long id,
        String name,
        String description,
        long testCaseCount,
        Instant createdAt,
        Instant updatedAt) {

    public static TestSuiteSummaryRes from(TestSuiteSummary summary) {
        return new TestSuiteSummaryRes(
                summary.id(),
                summary.name(),
                summary.description(),
                summary.testCaseCount(),
                summary.createdAt(),
                summary.updatedAt());
    }
}
