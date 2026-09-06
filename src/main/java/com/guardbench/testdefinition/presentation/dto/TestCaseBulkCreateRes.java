package com.guardbench.testdefinition.presentation.dto;

import java.util.List;

import com.guardbench.testdefinition.application.TestCaseBulkCreateResult;

public record TestCaseBulkCreateRes(
        List<Long> createdTestCaseIds,
        int createdCount,
        long totalTestCaseCount) {

    public static TestCaseBulkCreateRes from(TestCaseBulkCreateResult result) {
        return new TestCaseBulkCreateRes(
                result.createdTestCaseIds(), result.createdCount(), result.totalTestCaseCount());
    }
}
