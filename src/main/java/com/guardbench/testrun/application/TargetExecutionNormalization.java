package com.guardbench.testrun.application;

import com.guardbench.testrun.domain.ApplicationResponse;
import com.guardbench.testrun.domain.TestExecutionError;

public record TargetExecutionNormalization(ApplicationResponse applicationResponse, TestExecutionError error) {

    public TargetExecutionNormalization {
        if ((applicationResponse == null) == (error == null)) {
            throw new IllegalArgumentException("normalization must contain exactly one result or error");
        }
    }

    public static TargetExecutionNormalization succeeded(ApplicationResponse applicationResponse) {
        return new TargetExecutionNormalization(applicationResponse, null);
    }

    public static TargetExecutionNormalization failed(TestExecutionError error) {
        return new TargetExecutionNormalization(null, error);
    }

    public boolean isSuccess() {
        return applicationResponse != null;
    }

}
