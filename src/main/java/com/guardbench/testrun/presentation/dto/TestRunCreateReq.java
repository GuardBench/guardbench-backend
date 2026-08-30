package com.guardbench.testrun.presentation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import com.guardbench.testrun.application.TestRunCreateCommand;

/**
 * TestSuite의 활성 TestCase 전체를 실행 대상으로 사용하는 TestRun 접수 요청이다. TestCase ID 목록은
 * 받지 않는다.
 */
public record TestRunCreateReq(
        @NotNull(message = "testSuiteId는 필수입니다.")
        @Min(value = 1, message = "testSuiteId는 1 이상이어야 합니다.") Long testSuiteId,
        @NotNull(message = "target은 필수입니다.") @Valid TargetReferenceReq target
) {

    public TestRunCreateCommand toCommand(String idempotencyKey) {
        return new TestRunCreateCommand(
                testSuiteId,
                target.type(),
                target.identifier(),
                target.revision(),
                idempotencyKey
        );
    }
}
