package com.guardbench.testrun.application.port.out;

import java.util.Objects;

import com.guardbench.testrun.domain.TargetReference;

public record TargetPreparationRequest(TargetReference targetReference, long testRunId) {

    public TargetPreparationRequest {
        Objects.requireNonNull(targetReference, "target reference must not be null");
        if (testRunId <= 0) {
            throw new IllegalArgumentException("testRunId must be positive");
        }
    }

    public String idempotencyToken() {
        return "guardbench-test-run-" + testRunId;
    }
}
