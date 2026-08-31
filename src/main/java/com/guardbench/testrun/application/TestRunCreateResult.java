package com.guardbench.testrun.application;

import java.time.Instant;

import com.guardbench.testrun.application.port.out.TargetReferenceView;

/** TestRun 접수 응답을 위한 Application 계층 결과 값이다. */
public record TestRunCreateResult(long id, long testSuiteId, String status, int testCaseCount,
                                  TargetReferenceView target, Instant createdAt) {
    public TestRunCreateResult(long id, long testSuiteId, String status, int testCaseCount, Instant createdAt) {
        this(id, testSuiteId, status, testCaseCount,
                new TargetReferenceView("unknown", "BEDROCK_GUARDRAIL", "unknown", "DRAFT"), createdAt);
    }
}
