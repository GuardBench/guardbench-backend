package com.guardbench.testrun.application.port.out;

import java.util.Objects;

public record TargetExecutionResult(String actionCode, TargetFailureCode failureCode) {

    public TargetExecutionResult {
        if (actionCode != null && actionCode.isBlank()) {
            actionCode = null;
        }
        if ((actionCode == null) == (failureCode == null)) {
            throw new IllegalArgumentException("target result must contain exactly one action or failure");
        }
    }

    public static TargetExecutionResult succeeded(String actionCode) {
        return new TargetExecutionResult(Objects.requireNonNull(actionCode), null);
    }

    public static TargetExecutionResult failed(TargetFailureCode failureCode) {
        return new TargetExecutionResult(null, Objects.requireNonNull(failureCode));
    }

    public boolean isSuccess() {
        return actionCode != null;
    }
}
