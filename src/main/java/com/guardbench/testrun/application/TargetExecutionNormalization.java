package com.guardbench.testrun.application;

import com.guardbench.testrun.domain.ApplicationResponse;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.ActualResult;
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

    /** @deprecated Target response와 Evaluator verdict를 분리한 {@link #applicationResponse()}를 사용한다. */
    @Deprecated
    public ActualResult actualResult() {
        if (applicationResponse == null) {
            return null;
        }
        try {
            return new ActualResult(Action.fromCode(applicationResponse.value()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
