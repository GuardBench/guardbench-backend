package com.guardbench.testrun.application.port.out;

/**
 * Execution Worker가 Provider 호출에 필요한 불변 컨텍스트 값이다.
 *
 * @param targetReference     TestRun이 고정한 불투명 Target reference
 * @param input               TestCaseSnapshot의 input 텍스트
 * @param testRunId           TestRun ID (완료 이벤트 발행에 필요)
 * @param evaluatorReference  TestRun이 고정한 불투명 Evaluator reference
 */
public record ExecutionContext(
        String targetReference,
        String input,
        long testRunId,
        String evaluatorReference
) {

    public ExecutionContext {
        if (targetReference == null || targetReference.isBlank()) {
            throw new IllegalArgumentException("target reference must not be blank");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        if (testRunId <= 0) {
            throw new IllegalArgumentException("testRunId must be positive");
        }
    }
}
