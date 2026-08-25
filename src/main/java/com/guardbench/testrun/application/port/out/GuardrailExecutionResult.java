package com.guardbench.testrun.application.port.out;

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
