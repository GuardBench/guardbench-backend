package com.guardbench.testrun.application.port.out;

import java.util.Objects;

/**
 * Target 준비에 필요한 값 기반 Application 계약이다.
 *
 * <p>ADR 0006에 따라 외부 Target Adapter에 TestRun Domain VO를 노출하지 않는다.
 */
public record TargetPreparationRequest(String referenceId, long testRunId) {

    public TargetPreparationRequest {
        Objects.requireNonNull(referenceId, "target reference ID must not be null");
        if (referenceId.isBlank()) {
            throw new IllegalArgumentException("target reference ID must not be blank");
        }
        if (testRunId <= 0) {
            throw new IllegalArgumentException("testRunId must be positive");
        }
    }

    public String idempotencyToken() {
        return "guardbench-test-run-" + testRunId;
    }
}
