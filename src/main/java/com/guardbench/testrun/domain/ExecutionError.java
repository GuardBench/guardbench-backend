package com.guardbench.testrun.domain;

import java.util.Objects;

public record ExecutionError(String code, String message) {

    public ExecutionError {
        code = requireText(code, "code");
        message = requireText(message, "message");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
