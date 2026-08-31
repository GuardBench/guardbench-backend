package com.guardbench.testrun.application.port.out;

import java.util.Objects;

/**
 * Target 실행의 provider-independent 결과다.
 *
 * <p>Application Target은 자연어 응답을 반환하고, Evaluator만 {@code ALLOW}/{@code BLOCK} 판정을
 * 만든다. 성공 값은 Target의 자연어 {@link #response()}이며 action code가 아니다.
 */
public record TargetExecutionResult(String response, TargetFailureCode failureCode) {

    public TargetExecutionResult {
        if (response != null && response.isBlank()) {
            response = null;
        }
        if ((response == null) == (failureCode == null)) {
            throw new IllegalArgumentException("target result must contain exactly one response or failure");
        }
    }

    public static TargetExecutionResult succeeded(String response) {
        return new TargetExecutionResult(Objects.requireNonNull(response), null);
    }

    public static TargetExecutionResult failed(TargetFailureCode failureCode) {
        return new TargetExecutionResult(null, Objects.requireNonNull(failureCode));
    }

    public boolean isSuccess() {
        return response != null;
    }

}
