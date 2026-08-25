package com.guardbench.testrun.application.port.out;

import java.util.Objects;

public record GuardrailMaterializationRequest(String guardrailIdentifier, long testRunId) {

    public GuardrailMaterializationRequest {
        validateText(guardrailIdentifier, "guardrail identifier");
        if (testRunId <= 0) {
            throw new IllegalArgumentException("testRunId must be positive");
        }
    }

    /**
     * ADR 0005의 materialization 재시도 멱등성 계약을 Adapter 입력으로 고정한다.
     */
    public String clientRequestToken() {
        return "guardbench-test-run-" + testRunId;
    }

    private static void validateText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
