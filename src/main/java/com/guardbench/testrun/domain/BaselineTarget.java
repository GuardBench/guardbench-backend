package com.guardbench.testrun.domain;

import java.util.Objects;

public record BaselineTarget(String guardrailId, String version) {

    public BaselineTarget {
        validateText(guardrailId, "guardrail ID");
        validateVersion(version, "baseline version");
    }

    private static void validateText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void validateVersion(String value, String field) {
        validateText(value, field);
        if (!value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(field + " must be numbered");
        }
    }
}
