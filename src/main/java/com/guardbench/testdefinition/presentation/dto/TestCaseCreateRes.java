package com.guardbench.testdefinition.presentation.dto;

import java.time.Instant;

import com.guardbench.testdefinition.application.TestCaseDetail;

public record TestCaseCreateRes(
        long id, long testSuiteId, String name, String input, String expectedAction,
        String severity, String category, Instant createdAt, Instant updatedAt) {

    public static TestCaseCreateRes from(TestCaseDetail detail) {
        return new TestCaseCreateRes(
                detail.id(), detail.testSuiteId(), detail.name(), detail.input(),
                detail.expectedAction().name(), detail.severity().name(), detail.category(),
                detail.createdAt(), detail.updatedAt());
    }
}
