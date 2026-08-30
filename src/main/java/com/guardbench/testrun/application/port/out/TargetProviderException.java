package com.guardbench.testrun.application.port.out;

import java.util.Objects;

public final class TargetProviderException extends RuntimeException {

    private final TargetFailureCode failureCode;

    public TargetProviderException(TargetFailureCode failureCode) {
        super("target provider failure: " + Objects.requireNonNull(failureCode));
        this.failureCode = failureCode;
    }

    public TargetFailureCode failureCode() {
        return failureCode;
    }
}
