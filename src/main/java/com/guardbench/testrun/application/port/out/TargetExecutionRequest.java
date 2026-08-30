package com.guardbench.testrun.application.port.out;

import java.util.Objects;

import com.guardbench.testrun.domain.TargetReference;

public record TargetExecutionRequest(TargetReference targetReference, String input) {

    public TargetExecutionRequest {
        Objects.requireNonNull(targetReference, "target reference must not be null");
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
    }
}
