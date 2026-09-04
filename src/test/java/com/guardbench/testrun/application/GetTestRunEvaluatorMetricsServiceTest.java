package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.EvaluatorMetricsView;
import com.guardbench.testrun.application.port.out.LoadTestRunDetailPort;
import com.guardbench.testrun.application.port.out.LoadTestRunEvaluatorMetricsPort;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testrun.application.port.out.TestRunProgress;
import com.guardbench.testrun.application.port.out.TargetReferenceView;
import com.guardbench.testrun.domain.TestRunStatus;

import org.junit.jupiter.api.Test;

class GetTestRunEvaluatorMetricsServiceTest {

    private static final TargetReferenceView TARGET = new TargetReferenceView(
            "target-ref", "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", "test-model");

    @Test
    void returnsStoredMetricsWhenTestRunIsFinished() {
        EvaluatorMetricsView expected = new EvaluatorMetricsView(2L, 3L, 1L, 1L, 0.25, 1.0 / 3.0);
        LoadTestRunDetailPort detailPort = testRunId -> Optional.of(detailWithStatus(TestRunStatus.FINISHED));
        LoadTestRunEvaluatorMetricsPort metricsPort = testRunId -> expected;
        GetTestRunEvaluatorMetricsService service = new GetTestRunEvaluatorMetricsService(detailPort, metricsPort);

        assertEquals(expected, service.getMetrics(901L));
    }

    @Test
    void throwsNotFoundBeforeLoadingMetricsWhenTestRunDoesNotExist() {
        LoadTestRunDetailPort detailPort = testRunId -> Optional.empty();
        LoadTestRunEvaluatorMetricsPort metricsPort = testRunId -> {
            throw new AssertionError("metrics Port를 호출해서는 안 된다");
        };
        GetTestRunEvaluatorMetricsService service = new GetTestRunEvaluatorMetricsService(detailPort, metricsPort);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> service.getMetrics(999L));

        assertEquals(ApplicationErrorCode.TEST_RUN_NOT_FOUND, exception.errorCode());
    }

    @Test
    void throwsNotFinishedBeforeLoadingMetricsWhenTestRunIsRunning() {
        LoadTestRunDetailPort detailPort = testRunId -> Optional.of(detailWithStatus(TestRunStatus.RUNNING));
        LoadTestRunEvaluatorMetricsPort metricsPort = testRunId -> {
            throw new AssertionError("metrics Port를 호출해서는 안 된다");
        };
        GetTestRunEvaluatorMetricsService service = new GetTestRunEvaluatorMetricsService(detailPort, metricsPort);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> service.getMetrics(901L));

        assertEquals(ApplicationErrorCode.TEST_RUN_NOT_FINISHED, exception.errorCode());
    }

    private static TestRunDetail detailWithStatus(TestRunStatus status) {
        return new TestRunDetail(
                901L, 1L, status, 1,
                new TestRunProgress(1, 100.0), TARGET, null, null,
                Instant.parse("2026-08-24T14:30:00Z"), Instant.parse("2026-08-24T14:30:03Z"),
                null, Instant.parse("2026-08-24T14:31:20Z"));
    }
}
