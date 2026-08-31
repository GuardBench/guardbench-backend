package com.guardbench.testrun.application.port.out;

import java.util.Objects;

/** Evaluator 실행의 provider-independent 결과다. */
public record EvaluatorExecutionResult(String actionCode, EvaluatorFailureCode failureCode) {

    public EvaluatorExecutionResult {
        if (actionCode != null && actionCode.isBlank()) {
            actionCode = null;
        }
        if ((actionCode == null) == (failureCode == null)) {
            throw new IllegalArgumentException("evaluator result must contain exactly one action or failure");
        }
    }

    public static EvaluatorExecutionResult succeeded(String actionCode) {
        return new EvaluatorExecutionResult(Objects.requireNonNull(actionCode), null);
    }

    public static EvaluatorExecutionResult failed(EvaluatorFailureCode failureCode) {
        return new EvaluatorExecutionResult(null, Objects.requireNonNull(failureCode));
    }

    public boolean isSuccess() {
        return actionCode != null;
    }
}
