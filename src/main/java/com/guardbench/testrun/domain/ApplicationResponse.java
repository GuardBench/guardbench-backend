package com.guardbench.testrun.domain;

import java.util.Objects;

/** AI Application Target이 반환한 자연어 응답이다. */
public record ApplicationResponse(String value) {

    public ApplicationResponse {
        Objects.requireNonNull(value, "application response must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("application response must not be blank");
        }
    }
}
