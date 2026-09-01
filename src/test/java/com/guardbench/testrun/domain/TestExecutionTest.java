package com.guardbench.testrun.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestExecutionTest {

    private static final Instant STARTED_AT = Instant.parse("2026-08-25T00:00:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-08-25T00:00:01Z");

    @Test
    @DisplayName("SUCCEEDED 실행은 Application response, Evaluator verdict와 lifecycle 시각을 가진다")
    void succeededExecutionContainsApplicationAndEvaluationResults() {
        TestExecution execution = TestExecution.succeeded(
                executionId(),
                new ApplicationResponse("natural language response"),
                new EvaluationResult(Action.ALLOW),
                STARTED_AT,
                COMPLETED_AT
        );

        assertEquals(TestExecutionStatus.SUCCEEDED, execution.status());
        assertEquals(new ApplicationResponse("natural language response"), execution.applicationResponse());
        assertEquals(new EvaluationResult(Action.ALLOW), execution.evaluationResult());
        assertNull(execution.error());
        assertEquals(STARTED_AT, execution.startedAt());
        assertEquals(COMPLETED_AT, execution.completedAt());
    }

    @Test
    @DisplayName("FAILED 실행은 허용된 오류 code와 외부 공개용으로 가공된 detail만 가진다")
    void failedExecutionContainsOnlySafeError() {
        TestExecutionError error = new TestExecutionError(
                TestExecutionErrorCode.PROVIDER_UNAVAILABLE,
                "Provider is temporarily unavailable."
        );

        TestExecution execution = TestExecution.failed(
                executionId(),
                error,
                STARTED_AT,
                COMPLETED_AT
        );

        assertEquals(TestExecutionStatus.FAILED, execution.status());
        assertNull(execution.evaluationResult());
        assertEquals(error, execution.error());
        assertEquals(STARTED_AT, execution.startedAt());
        assertEquals(COMPLETED_AT, execution.completedAt());
    }

    @Test
    @DisplayName("TIMED_OUT 실행은 PROVIDER_TIMEOUT 오류를 요구한다")
    void timedOutExecutionRequiresProviderTimeoutError() {
        TestExecutionError wrongError = new TestExecutionError(
                TestExecutionErrorCode.PROVIDER_UNAVAILABLE,
                "Provider is temporarily unavailable."
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> TestExecution.timedOut(
                        executionId(),
                        wrongError,
                        STARTED_AT,
                        COMPLETED_AT
                )
        );
    }

    @Test
    @DisplayName("terminal 실행은 Application Clock 시작과 종료 시각을 모두 요구한다")
    void rejectsTerminalExecutionWithoutApplicationTimeline() {
        assertThrows(
                NullPointerException.class,
                () -> TestExecution.succeeded(
                        executionId(),
                        new ApplicationResponse("response"),
                        new EvaluationResult(Action.ALLOW),
                        null,
                        COMPLETED_AT
                )
        );
    }

    @Test
    @DisplayName("종료 시각이 시작 시각보다 앞선 terminal 실행은 생성할 수 없다")
    void rejectsTerminalExecutionCompletedBeforeStarted() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TestExecution.failed(
                        executionId(),
                        new TestExecutionError(TestExecutionErrorCode.PROVIDER_UNAVAILABLE, "Provider is unavailable."),
                        COMPLETED_AT,
                        STARTED_AT
                )
        );
    }

    @Test
    @DisplayName("NOT_STARTED 실행은 Application response, verdict, 오류와 lifecycle 시각을 가지지 않는다")
    void notStartedExecutionContainsNeitherResultErrorNorTimeline() {
        TestExecution execution = TestExecution.notStarted(executionId());

        assertEquals(TestExecutionStatus.NOT_STARTED, execution.status());
        assertNull(execution.applicationResponse());
        assertNull(execution.evaluationResult());
        assertNull(execution.error());
        assertNull(execution.startedAt());
        assertNull(execution.completedAt());
    }

    private static TestExecutionId executionId() {
        return new TestExecutionId(new TestCaseSnapshotId(1));
    }
}
