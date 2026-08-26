package com.guardbench.testrun.presentation.dto;

import java.time.Instant;

import com.guardbench.testrun.application.TestRunCreateResult;

/**
 * 새 접수는 {@code QUEUED}를 반환한다. 같은 Idempotency-Key와 같은 요청의 재전송은 기존 TestRun의
 * 현재 status를 반환할 수 있다. 실행 전에는 의미가 없는 {@code executionOutcome}과
 * {@code qualityGateStatus}는 포함하지 않는다.
 */
public record TestRunCreateRes(
        long id,
        long testSuiteId,
        String status,
        int testCaseCount,
        Instant createdAt
) {

    public static TestRunCreateRes from(TestRunCreateResult result) {
        return new TestRunCreateRes(
                result.id(),
                result.testSuiteId(),
                result.status(),
                result.testCaseCount(),
                result.createdAt()
        );
    }
}
