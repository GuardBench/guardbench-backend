package com.guardbench.testrun.application;

import com.guardbench.testrun.domain.ActualResult;
import com.guardbench.testrun.domain.TestExecutionError;

public record TargetExecutionNormalization(ActualResult actualResult, TestExecutionError error) {

    public TargetExecutionNormalization {
        if ((actualResult == null) == (error == null)) {
            throw new IllegalArgumentException("normalization must contain exactly one result or error");
        }
    }

    public static TargetExecutionNormalization succeeded(ActualResult actualResult) {
        return new TargetExecutionNormalization(actualResult, null);
    }

    public static TargetExecutionNormalization failed(TestExecutionError error) {
        return new TargetExecutionNormalization(null, error);
    }

    public boolean isSuccess() {
        return actualResult != null;
    }
}
