package com.guardbench.testrun.domain;

import java.util.Objects;

public record BaselineTarget(String guardrailId, String version) {

    public BaselineTarget {
        validateText(guardrailId, "guardrail ID");
        validateVersion(version, "baseline version");
    }

    private static void validateText(String value, String field) {
        if (isContractBlank(value)) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static boolean isContractBlank(String value) {
        return value == null || value.codePoints().allMatch(BaselineTarget::isContractWhitespace);
    }

    private static boolean isContractWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0xFEFF;
    }

    private static void validateVersion(String value, String field) {
        validateText(value, field);
        if (!value.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException(field + " must be numbered");
        }
    }
}
