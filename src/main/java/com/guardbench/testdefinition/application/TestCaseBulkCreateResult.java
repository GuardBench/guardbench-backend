package com.guardbench.testdefinition.application;

import java.util.List;

public record TestCaseBulkCreateResult(
        List<Long> createdTestCaseIds,
        long totalTestCaseCount) {

    public TestCaseBulkCreateResult {
        createdTestCaseIds = List.copyOf(createdTestCaseIds);
        if (createdTestCaseIds.isEmpty()) {
            throw new IllegalArgumentException("createdTestCaseIds must not be empty");
        }
        if (totalTestCaseCount < createdTestCaseIds.size()) {
            throw new IllegalArgumentException("totalTestCaseCount must include created TestCases");
        }
    }

    public int createdCount() {
        return createdTestCaseIds.size();
    }
}
