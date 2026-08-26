package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Instant;
import java.util.List;

import com.guardbench.testrun.application.port.out.LoadTestRunListPort;
import com.guardbench.testrun.application.port.out.PageCriteria;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.TestRunListCriteria;
import com.guardbench.testrun.application.port.out.TestRunListItem;
import com.guardbench.testrun.application.port.out.TestRunProgress;
import com.guardbench.testrun.domain.TestRunExecutionOutcome;
import com.guardbench.testrun.domain.TestRunStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GetTestRunListServiceTest {

    @Test
    @DisplayName("목록 조회는 Port가 반환한 페이지 결과를 그대로 전달한다")
    void returnsPageResultFromPort() {
        TestRunListItem item = new TestRunListItem(
                901L, 1L, TestRunStatus.FINISHED, 253,
                new TestRunProgress(253, 100.0), TestRunExecutionOutcome.COMPLETED, "PASS",
                Instant.parse("2026-08-24T14:30:00Z"), Instant.parse("2026-08-24T14:30:03Z"),
                Instant.parse("2026-08-24T14:35:00Z"), Instant.parse("2026-08-24T14:35:00Z"));
        PageResult<TestRunListItem> expected = PageResult.of(
                List.of(item), new PageCriteria(1, 20), 1L);
        LoadTestRunListPort port = criteria -> expected;
        GetTestRunListService service = new GetTestRunListService(port);

        PageResult<TestRunListItem> actual = service.getTestRuns(TestRunListCriteria.firstPage());

        assertSame(expected, actual);
        assertEquals(1, actual.items().size());
    }
}
