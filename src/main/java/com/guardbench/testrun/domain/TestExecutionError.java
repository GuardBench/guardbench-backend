package com.guardbench.testrun.domain;

import java.util.Objects;

public record TestExecutionError(
        TestExecutionErrorStage stage,
        TestExecutionErrorCode code,
        String message) {

    public TestExecutionError {
        Objects.requireNonNull(stage, "error stage must not be null");
        Objects.requireNonNull(code, "error code must not be null");
        if (isContractBlank(message)) {
            throw new IllegalArgumentException("error message must not be blank");
        }
    }

    private static boolean isContractBlank(String value) {
        return value == null || value.codePoints().allMatch(TestExecutionError::isContractWhitespace);
    }

    private static boolean isContractWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0xFEFF;
    }
}
