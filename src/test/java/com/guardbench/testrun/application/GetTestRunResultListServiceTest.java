package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.LoadTestRunDetailPort;
import com.guardbench.testrun.application.port.out.LoadTestRunResultListPort;
import com.guardbench.testrun.application.port.out.PageCriteria;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.TestExecutionView;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testrun.application.port.out.TestRunProgress;
import com.guardbench.testrun.application.port.out.TestRunResultItem;
import com.guardbench.testrun.application.port.out.TestRunResultListCriteria;
import com.guardbench.testrun.application.port.out.TargetReferenceView;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.TestExecutionStatus;
import com.guardbench.testrun.domain.TestRunStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetTestRunResultListServiceTest {

    private static final TargetReferenceView TARGET = new TargetReferenceView("target-ref");

    @Test
    @DisplayName("FINISHED TestRun의 결과를 조회하면 Port의 결과 페이지를 그대로 반환한다")
    void returnsResultsWhenTestRunFinished() {
        TestRunDetail finishedTestRun = detailWithStatus(TestRunStatus.FINISHED);
        TestRunResultItem resultItem = new TestRunResultItem(
                1001L, 10L, "case", "input", Action.BLOCK, Severity.HIGH, "category",
                new TestExecutionView(TestExecutionStatus.SUCCEEDED, Action.ALLOW, null, null),
                "FAIL");
        PageResult<TestRunResultItem> expected =
                PageResult.of(List.of(resultItem), new PageCriteria(1, 20), 1L);
        LoadTestRunDetailPort detailPort = testRunId -> Optional.of(finishedTestRun);
        LoadTestRunResultListPort resultPort = (testRunId, criteria) -> expected;
        GetTestRunResultListService service = new GetTestRunResultListService(detailPort, resultPort);

        PageResult<TestRunResultItem> actual =
                service.getResults(901L, TestRunResultListCriteria.firstPage());

        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("존재하지 않는 TestRun의 결과를 조회하면 TEST_RUN_NOT_FOUND 예외를 던진다")
    void throwsNotFoundWhenTestRunDoesNotExist() {
        LoadTestRunDetailPort detailPort = testRunId -> Optional.empty();
        LoadTestRunResultListPort resultPort = (testRunId, criteria) -> {
            throw new AssertionError("존재하지 않는 TestRun의 결과 Port를 호출해서는 안 된다");
        };
        GetTestRunResultListService service = new GetTestRunResultListService(detailPort, resultPort);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> service.getResults(999L, TestRunResultListCriteria.firstPage()));

        assertEquals(ApplicationErrorCode.TEST_RUN_NOT_FOUND, exception.errorCode());
    }

    @Test
    @DisplayName("아직 종료되지 않은 TestRun의 결과를 조회하면 TEST_RUN_NOT_FINISHED 예외를 던진다")
    void throwsNotFinishedWhenTestRunIsRunning() {
        TestRunDetail runningTestRun = detailWithStatus(TestRunStatus.RUNNING);
        LoadTestRunDetailPort detailPort = testRunId -> Optional.of(runningTestRun);
        LoadTestRunResultListPort resultPort = (testRunId, criteria) -> {
            throw new AssertionError("종료되지 않은 TestRun의 결과 Port를 호출해서는 안 된다");
        };
        GetTestRunResultListService service = new GetTestRunResultListService(detailPort, resultPort);

        ApplicationException exception = assertThrows(ApplicationException.class,
                () -> service.getResults(901L, TestRunResultListCriteria.firstPage()));

        assertEquals(ApplicationErrorCode.TEST_RUN_NOT_FINISHED, exception.errorCode());
    }

    private static TestRunDetail detailWithStatus(TestRunStatus status) {
        return new TestRunDetail(
                901L, 1L, status, 253,
                new TestRunProgress(253, 100.0), TARGET, null, null,
                Instant.parse("2026-08-24T14:30:00Z"), Instant.parse("2026-08-24T14:30:03Z"),
                null, Instant.parse("2026-08-24T14:31:20Z"));
    }
}
