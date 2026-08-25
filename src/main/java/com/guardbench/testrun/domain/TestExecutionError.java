package com.guardbench.testrun.domain;

import java.util.Objects;

public record TestExecutionError(TestExecutionErrorCode code, String message) {

    public TestExecutionError {
        Objects.requireNonNull(code, "error code must not be null");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("error message must not be blank");
        }
    }
}
