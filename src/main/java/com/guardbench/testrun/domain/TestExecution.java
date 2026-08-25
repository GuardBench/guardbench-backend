package com.guardbench.testrun.domain;

import java.time.Instant;
import java.util.Objects;

public final class TestExecution {

    private final TestExecutionId id;
    private final TestExecutionStatus status;
    private final ActualResult actualResult;
    private final TestExecutionError error;
    private final Instant startedAt;
    private final Instant completedAt;

    private TestExecution(
            TestExecutionId id,
            TestExecutionStatus status,
            ActualResult actualResult,
            TestExecutionError error,
            Instant startedAt,
            Instant completedAt
    ) {
        this.id = Objects.requireNonNull(id, "execution ID must not be null");
        this.status = Objects.requireNonNull(status, "execution status must not be null");
        this.actualResult = actualResult;
        this.error = error;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        validateResultShape();
        validateTimeline();
    }

    public static TestExecution succeeded(
            TestExecutionId id,
            ActualResult actualResult,
            Instant startedAt,
            Instant completedAt
    ) {
        return new TestExecution(id, TestExecutionStatus.SUCCEEDED, actualResult, null, startedAt, completedAt);
    }

    public static TestExecution failed(
            TestExecutionId id,
            TestExecutionError error,
            Instant startedAt,
            Instant completedAt
    ) {
        return new TestExecution(id, TestExecutionStatus.FAILED, null, error, startedAt, completedAt);
    }

    public static TestExecution timedOut(
            TestExecutionId id,
            TestExecutionError error,
            Instant startedAt,
            Instant completedAt
    ) {
        if (error == null || error.code() != TestExecutionErrorCode.PROVIDER_TIMEOUT) {
            throw new IllegalArgumentException("TIMED_OUT execution requires PROVIDER_TIMEOUT error");
        }
        return new TestExecution(id, TestExecutionStatus.TIMED_OUT, null, error, startedAt, completedAt);
    }

    public static TestExecution notStarted(TestExecutionId id) {
        return new TestExecution(id, TestExecutionStatus.NOT_STARTED, null, null, null, null);
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

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
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

    private void validateTimeline() {
        if (status == TestExecutionStatus.NOT_STARTED) {
            if (startedAt != null || completedAt != null) {
                throw new IllegalArgumentException("NOT_STARTED execution must not have execution times");
            }
            return;
        }

        Objects.requireNonNull(startedAt, "terminal execution start time must not be null");
        Objects.requireNonNull(completedAt, "terminal execution completion time must not be null");
        if (completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("execution completion time must not be before start time");
        }
    }
}
