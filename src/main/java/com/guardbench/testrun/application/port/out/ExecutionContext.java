package com.guardbench.testrun.application.port.out;

/**
 * Execution Worker가 Provider 호출에 필요한 불변 컨텍스트 값이다.
 *
 * @param guardrailIdentifier TestRun의 guardrail ID
 * @param guardrailVersion    target type에 따른 확정 numeric version
 * @param input               TestCaseSnapshot의 input 텍스트
 * @param testRunId           TestRun ID (완료 이벤트 발행에 필요)
 */
public record ExecutionContext(
        String guardrailIdentifier,
        String guardrailVersion,
        String input,
        long testRunId
) {

    public ExecutionContext {
        if (guardrailIdentifier == null || guardrailIdentifier.isBlank()) {
            throw new IllegalArgumentException("guardrail identifier must not be blank");
        }
        if (guardrailVersion == null || guardrailVersion.isBlank()) {
            throw new IllegalArgumentException("guardrail version must not be blank");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        if (testRunId <= 0) {
            throw new IllegalArgumentException("testRunId must be positive");
        }
    }
}
