package com.guardbench.testrun.application;

import java.time.Instant;

/**
 * TestRun 접수 응답을 위한 Application 계층 결과 값이다.
 */
public record TestRunCreateResult(
        long id,
        long testSuiteId,
        String status,
        int testCaseCount,
        Instant createdAt
) {
}
