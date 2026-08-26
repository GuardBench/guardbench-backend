package com.guardbench.testrun.application.port.out;

import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.TestExecutionStatus;

public record TestExecutionView(
        TestExecutionStatus status,
        Action actualAction,
        String errorCode,
        String errorMessage) {
    public TestExecutionView {
        if (status == null) {
            throw new IllegalArgumentException("execution status must not be null");
        }
        if (status == TestExecutionStatus.SUCCEEDED && actualAction == null) {
            throw new IllegalArgumentException("successful execution requires actualAction");
        }
        if (status != TestExecutionStatus.SUCCEEDED && actualAction != null) {
            throw new IllegalArgumentException("non-success execution cannot have actualAction");
        }
        if ((errorCode == null) != (errorMessage == null)) {
            throw new IllegalArgumentException("execution error code and message must be paired");
        }
    }
}
