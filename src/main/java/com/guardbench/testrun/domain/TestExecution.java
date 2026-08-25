package com.guardbench.testrun.domain;

import java.util.Objects;

public final class TestExecution {

    private final TestExecutionId id;
    private final TestExecutionStatus status;
    private final ActualResult actualResult;
    private final TestExecutionError error;

    private TestExecution(
            TestExecutionId id,
            TestExecutionStatus status,
            ActualResult actualResult,
            TestExecutionError error
    ) {
        this.id = Objects.requireNonNull(id, "execution ID must not be null");
        this.status = Objects.requireNonNull(status, "execution status must not be null");
        this.actualResult = actualResult;
        this.error = error;
        validateResultShape();
    }

    public static TestExecution succeeded(TestExecutionId id, ActualResult actualResult) {
        return new TestExecution(id, TestExecutionStatus.SUCCEEDED, actualResult, null);
    }

    public static TestExecution failed(TestExecutionId id, TestExecutionError error) {
        return new TestExecution(id, TestExecutionStatus.FAILED, null, error);
    }

    public static TestExecution timedOut(TestExecutionId id, TestExecutionError error) {
        if (error == null || error.code() != TestExecutionErrorCode.PROVIDER_TIMEOUT) {
            throw new IllegalArgumentException("TIMED_OUT execution requires PROVIDER_TIMEOUT error");
        }
        return new TestExecution(id, TestExecutionStatus.TIMED_OUT, null, error);
    }

    public static TestExecution notStarted(TestExecutionId id) {
        return new TestExecution(id, TestExecutionStatus.NOT_STARTED, null, null);
    }

    public TestExecutionId id() {
        return id;
    }

    public TestExecutionStatus status() {
        return status;
    }

    public ActualResult actualResult() {
        return actualResult;
    }

    public TestExecutionError error() {
        return error;
    }

    private void validateResultShape() {
        boolean hasActualResult = actualResult != null;
        boolean hasError = error != null;

        switch (status) {
            case SUCCEEDED -> {
                if (!hasActualResult || hasError) {
                    throw new IllegalArgumentException("SUCCEEDED execution requires only an actual result");
                }
            }
            case FAILED, TIMED_OUT -> {
                if (hasActualResult || !hasError) {
                    throw new IllegalArgumentException(status + " execution requires only an error");
                }
            }
            case NOT_STARTED -> {
                if (hasActualResult || hasError) {
                    throw new IllegalArgumentException("NOT_STARTED execution must not have a result or error");
                }
            }
        }
    }
}
