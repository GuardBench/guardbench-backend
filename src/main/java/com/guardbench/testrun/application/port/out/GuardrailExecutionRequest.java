package com.guardbench.testrun.application.port.out;

public record GuardrailExecutionRequest(
        String guardrailIdentifier,
        String guardrailVersion,
        String input
) {

    public GuardrailExecutionRequest {
        validateText(guardrailIdentifier, "guardrail identifier");
        if (guardrailVersion == null || !guardrailVersion.matches("[1-9][0-9]{0,7}")) {
            throw new IllegalArgumentException("guardrail version must be a positive numeric version");
        }
        validateText(input, "input");
    }

    private static void validateText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
