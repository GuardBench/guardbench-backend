package com.guardbench.testrun.presentation.dto;

import java.time.Instant;

import com.guardbench.testrun.application.TestRunCreateResult;

/** 새 접수 또는 멱등 재전송으로 반환하는 TestRun 요약이다. */
public record TestRunCreateRes(long id, long testSuiteId, String status, int testCaseCount,
                               TargetReferenceRes target, Instant createdAt) {
    public static TestRunCreateRes from(TestRunCreateResult result) {
        return new TestRunCreateRes(result.id(), result.testSuiteId(), result.status(), result.testCaseCount(),
                new TargetReferenceRes(result.target().referenceId(), result.target().type(),
                        result.target().identifier(), result.target().revision(), result.target().model()), result.createdAt());
    }
}
