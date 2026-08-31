package com.guardbench.testrun.application.port.out;

import java.util.Objects;

/** Evaluator adapter가 application worker에 전달하는 안전한 provider 오류다. */
public final class EvaluatorProviderException extends RuntimeException {

    private final EvaluatorFailureCode failureCode;

    public EvaluatorProviderException(EvaluatorFailureCode failureCode) {
        super("evaluator provider failure: " + Objects.requireNonNull(failureCode));
        this.failureCode = failureCode;
    }

    public EvaluatorFailureCode failureCode() {
        return failureCode;
    }
}
