package com.guardbench.testrun.application.port.out;

/**
 * Guardrail Adapter가 provider response를 해석해 만든 provider-independent binary action 결과다.
 *
 * <p>성공 action은 {@code ALLOW} 또는 {@code BLOCK}만 허용하며, provider raw action·assessment·output은
 * 이 경계를 넘지 않는다.
 */
public record GuardrailExecutionResult(String actionCode, GuardrailFailureCode failureCode) {

    public GuardrailExecutionResult {
        boolean hasAction = actionCode != null && !actionCode.isBlank();
        boolean hasFailure = failureCode != null;
        if (hasAction == hasFailure) {
            throw new IllegalArgumentException("execution result must contain exactly one action or failure");
        }
    }

    public static GuardrailExecutionResult succeeded(String actionCode) {
        return new GuardrailExecutionResult(actionCode, null);
    }

    public static GuardrailExecutionResult failed(GuardrailFailureCode failureCode) {
        return new GuardrailExecutionResult(null, failureCode);
    }

    public boolean isSuccess() {
        return actionCode != null && !actionCode.isBlank();
    }
}
