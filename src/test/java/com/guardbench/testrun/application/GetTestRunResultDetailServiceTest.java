package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.LoadTestRunDetailPort;
import com.guardbench.testrun.application.port.out.LoadTestRunResultDetailPort;
import com.guardbench.testrun.application.port.out.TargetReferenceView;
import com.guardbench.testrun.application.port.out.TestExecutionView;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testrun.application.port.out.TestRunProgress;
import com.guardbench.testrun.application.port.out.TestRunResultAttentionType;
import com.guardbench.testrun.application.port.out.TestRunResultDetail;
import com.guardbench.testrun.application.port.out.TestRunResultItem;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.TestExecutionStatus;
import com.guardbench.testrun.domain.TestRunStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetTestRunResultDetailServiceTest {

    private static final TargetReferenceView TARGET = new TargetReferenceView(
            "target-ref", "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", "test-model");

    @Test
    @DisplayName("FINISHED TestRun의 결과 상세를 조회하면 저장된 Port 결과를 반환한다")
    void returnsStoredResultWhenTestRunFinished() {
        TestRunResultDetail expected = resultDetail("stored application response");
        LoadTestRunDetailPort detailPort = testRunId -> Optional.of(detailWithStatus(TestRunStatus.FINISHED));
        LoadTestRunResultDetailPort resultPort = (testRunId, snapshotId) -> Optional.of(expected);
        GetTestRunResultDetailService service = new GetTestRunResultDetailService(detailPort, resultPort);

        TestRunResultDetail actual = service.getResult(901L, 1001L);

        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("존재하지 않는 TestRun의 결과 상세를 조회하면 TEST_RUN_NOT_FOUND 예외를 던진다")
    void throwsNotFoundWhenTestRunDoesNotExist() {
        LoadTestRunDetailPort detailPort = testRunId -> Optional.empty();
        LoadTestRunResultDetailPort resultPort = (testRunId, snapshotId) -> {
            throw new AssertionError("존재하지 않는 TestRun의 결과 Port를 호출해서는 안 된다");
        };
        GetTestRunResultDetailService service = new GetTestRunResultDetailService(detailPort, resultPort);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> service.getResult(999L, 1001L));

        assertEquals(ApplicationErrorCode.TEST_RUN_NOT_FOUND, exception.errorCode());
    }

    @Test
    @DisplayName("아직 종료되지 않은 TestRun의 결과 상세를 조회하면 TEST_RUN_NOT_FINISHED 예외를 던진다")
    void throwsNotFinishedWhenTestRunIsRunning() {
        LoadTestRunDetailPort detailPort = testRunId -> Optional.of(detailWithStatus(TestRunStatus.RUNNING));
        LoadTestRunResultDetailPort resultPort = (testRunId, snapshotId) -> {
            throw new AssertionError("종료되지 않은 TestRun의 결과 Port를 호출해서는 안 된다");
        };
        GetTestRunResultDetailService service = new GetTestRunResultDetailService(detailPort, resultPort);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> service.getResult(901L, 1001L));

        assertEquals(ApplicationErrorCode.TEST_RUN_NOT_FINISHED, exception.errorCode());
    }

    @Test
    @DisplayName("TestRun에 속하지 않는 Snapshot 결과를 조회하면 TEST_RUN_RESULT_NOT_FOUND 예외를 던진다")
    void throwsResultNotFoundWhenSnapshotDoesNotBelongToTestRun() {
        LoadTestRunDetailPort detailPort = testRunId -> Optional.of(detailWithStatus(TestRunStatus.FINISHED));
        LoadTestRunResultDetailPort resultPort = (testRunId, snapshotId) -> Optional.empty();
        GetTestRunResultDetailService service = new GetTestRunResultDetailService(detailPort, resultPort);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> service.getResult(901L, 2002L));

        assertEquals(ApplicationErrorCode.TEST_RUN_RESULT_NOT_FOUND, exception.errorCode());
    }

    private static TestRunResultDetail resultDetail(String applicationResponse) {
        TestRunResultItem item = new TestRunResultItem(
                1001L, 10L, "case", "input", Action.BLOCK, Severity.HIGH, "category",
                new TestExecutionView(TestExecutionStatus.SUCCEEDED, Action.ALLOW, null, null, null),
                "FAIL", "FALSE_NEGATIVE", TestRunResultAttentionType.FALSE_NEGATIVE);
        return new TestRunResultDetail(item, applicationResponse);
    }

    private static TestRunDetail detailWithStatus(TestRunStatus status) {
        return new TestRunDetail(
                901L, 1L, status, 253,
                new TestRunProgress(253, 100.0), TARGET, null, null,
                Instant.parse("2026-08-24T14:30:00Z"), Instant.parse("2026-08-24T14:30:03Z"),
                null, Instant.parse("2026-08-24T14:31:20Z"));
    }
}
