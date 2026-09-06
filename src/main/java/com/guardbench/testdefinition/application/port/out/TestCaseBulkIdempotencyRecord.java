package com.guardbench.testdefinition.application.port.out;

import java.util.List;

public record TestCaseBulkIdempotencyRecord(
        String requestFingerprint,
        long testSuiteId,
        List<Long> createdTestCaseIds,
        long totalTestCaseCount) {

    public TestCaseBulkIdempotencyRecord {
        if (requestFingerprint == null || requestFingerprint.length() != 64) {
            throw new IllegalArgumentException("requestFingerprint must be a 64-char SHA-256 hex");
        }
        if (testSuiteId <= 0) {
            throw new IllegalArgumentException("testSuiteId must be positive");
        }
        createdTestCaseIds = List.copyOf(createdTestCaseIds);
        if (createdTestCaseIds.isEmpty() || createdTestCaseIds.size() > 1000) {
            throw new IllegalArgumentException("createdTestCaseIds size must be between 1 and 1000");
        }
        if (totalTestCaseCount < createdTestCaseIds.size()) {
            throw new IllegalArgumentException("totalTestCaseCount must include created TestCases");
        }
    }
}
