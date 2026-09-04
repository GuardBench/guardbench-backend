package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Optional;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.LoadTestRunDetailPort;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testrun.application.port.out.TestRunProgress;
import com.guardbench.testrun.application.port.out.TargetReferenceView;
import com.guardbench.testrun.domain.TestRunStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetTestRunDetailServiceTest {

    private static final TargetReferenceView TARGET = new TargetReferenceView(
            "target-ref", "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", "test-model");

    @Test
    @DisplayName("존재하는 TestRun을 조회하면 Port의 상세 결과를 그대로 반환한다")
    void returnsDetailWhenTestRunExists() {
        TestRunDetail expected = new TestRunDetail(
                901L, 1L, TestRunStatus.RUNNING, 253,
                new TestRunProgress(120, 47.43), TARGET, null, null,
                Instant.parse("2026-08-24T14:30:00Z"), Instant.parse("2026-08-24T14:30:03Z"),
                null, Instant.parse("2026-08-24T14:31:20Z"));
        LoadTestRunDetailPort port = testRunId -> Optional.of(expected);
        GetTestRunDetailService service = new GetTestRunDetailService(port);

        TestRunDetail actual = service.getTestRun(901L);

        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("존재하지 않는 TestRun을 조회하면 TEST_RUN_NOT_FOUND 예외를 던진다")
    void throwsNotFoundWhenTestRunDoesNotExist() {
        LoadTestRunDetailPort port = testRunId -> Optional.empty();
        GetTestRunDetailService service = new GetTestRunDetailService(port);

        ApplicationException exception =
                assertThrows(ApplicationException.class, () -> service.getTestRun(999L));

        assertEquals(ApplicationErrorCode.TEST_RUN_NOT_FOUND, exception.errorCode());
    }
}
