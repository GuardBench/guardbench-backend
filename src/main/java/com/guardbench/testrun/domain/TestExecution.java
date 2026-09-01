package com.guardbench.testrun.domain;

import java.time.Instant;
import java.util.Objects;

public final class TestExecution {

    private final TestExecutionId id;
    private final TestExecutionStatus status;
    private final ApplicationResponse applicationResponse;
    private final EvaluationResult evaluationResult;
    private final TestExecutionError error;
    private final Instant startedAt;
    private final Instant completedAt;

    private TestExecution(
            TestExecutionId id,
            TestExecutionStatus status,
            ApplicationResponse applicationResponse,
            EvaluationResult evaluationResult,
            TestExecutionError error,
            Instant startedAt,
            Instant completedAt
    ) {
        this.id = Objects.requireNonNull(id, "execution ID must not be null");
        this.status = Objects.requireNonNull(status, "execution status must not be null");
        this.applicationResponse = applicationResponse;
        this.evaluationResult = evaluationResult;
        this.error = error;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        validateResultShape();
        validateTimeline();
    }

    public static TestExecution succeeded(
            TestExecutionId id,
            ApplicationResponse applicationResponse,
            EvaluationResult evaluationResult,
            Instant startedAt,
            Instant completedAt
    ) {
        Objects.requireNonNull(applicationResponse, "successful execution requires application response");
        Objects.requireNonNull(evaluationResult, "successful execution requires evaluation result");
        return new TestExecution(id, TestExecutionStatus.SUCCEEDED, applicationResponse, evaluationResult,
                null, startedAt, completedAt);
    }

    public static TestExecution failed(
            TestExecutionId id,
            TestExecutionError error,
            Instant startedAt,
            Instant completedAt
    ) {
        return new TestExecution(id, TestExecutionStatus.FAILED, null, null, error, startedAt, completedAt);
    }

    /** Evaluator 실패처럼 Target response는 저장할 수 있는 terminal 오류다. */
    public static TestExecution failedAfterApplication(
            TestExecutionId id,
            ApplicationResponse applicationResponse,
            TestExecutionError error,
            Instant startedAt,
            Instant completedAt
    ) {
        Objects.requireNonNull(applicationResponse, "application response must not be null");
        if (error.stage() != TestExecutionErrorStage.EVALUATOR) {
            throw new IllegalArgumentException("application response may only accompany evaluator failure");
        }
        return new TestExecution(id, TestExecutionStatus.FAILED, applicationResponse, null,
                error, startedAt, completedAt);
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
        return new TestExecution(id, TestExecutionStatus.TIMED_OUT, null, null, error, startedAt, completedAt);
    }

    /** Evaluator timeout처럼 Target response는 저장할 수 있는 terminal timeout이다. */
    public static TestExecution timedOutAfterApplication(
            TestExecutionId id,
            ApplicationResponse applicationResponse,
            TestExecutionError error,
            Instant startedAt,
            Instant completedAt
    ) {
        if (error == null || error.stage() != TestExecutionErrorStage.EVALUATOR
                || error.code() != TestExecutionErrorCode.PROVIDER_TIMEOUT) {
            throw new IllegalArgumentException("evaluator timeout requires evaluator PROVIDER_TIMEOUT error");
        }
        Objects.requireNonNull(applicationResponse, "application response must not be null");
        return new TestExecution(id, TestExecutionStatus.TIMED_OUT, applicationResponse, null,
                error, startedAt, completedAt);
    }

    public static TestExecution notStarted(TestExecutionId id) {
        return new TestExecution(id, TestExecutionStatus.NOT_STARTED, null, null, null, null, null);
    }

    public TestExecutionId id() {
        return id;
    }

    public TestExecutionStatus status() {
        return status;
    }

    public ApplicationResponse applicationResponse() {
        return applicationResponse;
    }

    public EvaluationResult evaluationResult() {
        return evaluationResult;
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
        boolean hasApplicationResponse = applicationResponse != null;
        boolean hasEvaluationResult = evaluationResult != null;
        boolean hasError = error != null;

        switch (status) {
            case SUCCEEDED -> {
                if (!hasEvaluationResult || hasError) {
                    throw new IllegalArgumentException("SUCCEEDED execution requires only an evaluation result");
                }
            }
            case FAILED, TIMED_OUT -> {
                if (hasEvaluationResult || !hasError) {
                    throw new IllegalArgumentException(status + " execution requires only an error");
                }
            }
            case NOT_STARTED -> {
                if (hasApplicationResponse || hasEvaluationResult || hasError) {
                    throw new IllegalArgumentException("NOT_STARTED execution must not have response, result or error");
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
