package com.guardbench.testrun.domain;

import java.time.Instant;
import java.util.Objects;

public final class TestExecution {

    private final TestExecutionId id;
    private final TestExecutionResultStatus status;
    private final ActualResult actualResult;
    private final ExecutionError error;
    private final Instant startedAt;
    private final Instant completedAt;

    private TestExecution(
            TestExecutionId id,
            TestExecutionResultStatus status,
            ActualResult actualResult,
            ExecutionError error,
            Instant startedAt,
            Instant completedAt) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.actualResult = actualResult;
        this.error = error;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        validateShape();
    }

    public static TestExecution notStarted(TestExecutionId id) {
        return new TestExecution(id, TestExecutionResultStatus.NOT_STARTED, null, null, null, null);
    }

    public static TestExecution succeeded(TestExecutionId id, ActualResult actualResult,
            Instant startedAt, Instant completedAt) {
        return new TestExecution(id, TestExecutionResultStatus.SUCCEEDED, actualResult, null, startedAt, completedAt);
    }

    public static TestExecution failed(TestExecutionId id, ExecutionError error,
            Instant startedAt, Instant completedAt) {
        return new TestExecution(id, TestExecutionResultStatus.FAILED, null, error, startedAt, completedAt);
    }

    public static TestExecution timedOut(TestExecutionId id, ExecutionError error,
            Instant startedAt, Instant completedAt) {
        return new TestExecution(id, TestExecutionResultStatus.TIMED_OUT, null, error, startedAt, completedAt);
    }

    public TestExecutionId id() { return id; }

    public TestExecutionResultStatus status() { return status; }

    public ActualResult actualResult() { return actualResult; }

    public ExecutionError error() { return error; }

    public Instant startedAt() { return startedAt; }

    public Instant completedAt() { return completedAt; }

    public boolean isTerminal() { return true; }

    private void validateShape() {
        if (status == TestExecutionResultStatus.NOT_STARTED) {
            if (actualResult != null || error != null || startedAt != null || completedAt != null) {
                throw new IllegalArgumentException("NOT_STARTED execution cannot have result, error, or timestamps");
            }
            return;
        }
        if (startedAt == null || completedAt == null || completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("terminal execution must have ordered timestamps");
        }
        if (status == TestExecutionResultStatus.SUCCEEDED && (actualResult == null || error != null)) {
            throw new IllegalArgumentException("SUCCEEDED execution requires an actual result and no error");
        }
        if (status != TestExecutionResultStatus.SUCCEEDED && actualResult != null) {
            throw new IllegalArgumentException("failed execution cannot have an actual result");
        }
    }
}
