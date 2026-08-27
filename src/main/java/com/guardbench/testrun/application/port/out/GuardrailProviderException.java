package com.guardbench.testrun.application.port.out;

import java.util.Objects;

/**
 * Guardrail provider 호출 실패를 SDK 예외·원문 없이 안정적인 code로 전달한다.
 */
public final class GuardrailProviderException extends RuntimeException {

    private final GuardrailFailureCode failureCode;

    public GuardrailProviderException(GuardrailFailureCode failureCode) {
        super(Objects.requireNonNull(failureCode, "failure code must not be null").name(), null, false, false);
        this.failureCode = failureCode;
    }

    public GuardrailFailureCode failureCode() {
        return failureCode;
    }
}
