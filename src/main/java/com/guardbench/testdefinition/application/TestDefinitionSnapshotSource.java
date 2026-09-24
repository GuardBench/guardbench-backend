package com.guardbench.testdefinition.application;

import java.util.Objects;

/**
 * TestRun 등 다른 Context가 현재 TestCase 정의를 Snapshot으로 복제할 때 사용하는
 * TestDefinition Application 경계의 scalar/code projection이다.
 *
 * <p>다른 Context에 TestDefinition Domain 타입, ID VO 또는 Enum을 노출하지 않는다.
 */
public record TestDefinitionSnapshotSource(
        long testSuiteId,
        long testCaseId,
        String name,
        String input,
        String expectedActionCode,
        String severityCode,
        String category
) {
    public TestDefinitionSnapshotSource {
        if (testSuiteId <= 0 || testCaseId <= 0) {
            throw new IllegalArgumentException("source identifiers must be positive");
        }
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(expectedActionCode, "expected action code must not be null");
        Objects.requireNonNull(severityCode, "severity code must not be null");
        Objects.requireNonNull(category, "category must not be null");
    }
}
