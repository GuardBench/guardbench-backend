package com.guardbench.testrun.application;

import com.guardbench.testrun.domain.ActualResult;
import com.guardbench.testrun.domain.TestExecutionError;

public record GuardrailExecutionNormalization(ActualResult actualResult, TestExecutionError error) {

    public GuardrailExecutionNormalization {
        if ((actualResult == null) == (error == null)) {
            throw new IllegalArgumentException("normalization must contain exactly one result or error");
        }
    }

    public static GuardrailExecutionNormalization succeeded(ActualResult actualResult) {
        return new GuardrailExecutionNormalization(actualResult, null);
    }

    public static GuardrailExecutionNormalization failed(TestExecutionError error) {
        return new GuardrailExecutionNormalization(null, error);
    }

    public boolean isSuccess() {
        return actualResult != null;
    }
}
