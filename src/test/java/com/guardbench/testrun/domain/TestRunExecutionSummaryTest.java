package com.guardbench.testrun.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestRunExecutionSummaryTest {

    @Test
    @DisplayName("단일 실행이 terminal인 Snapshot만 처리 완료로 계산한다")
    void countsOnlySnapshotsWithTerminalExecutionAsProcessed() {
        TestRunExecutionSummary summary = TestRunExecutionSummary.from(List.of(
                status(1, TestExecutionStatus.FAILED),
                status(2, null)
        ));

        assertEquals(2, summary.testCaseCount());
        assertEquals(1, summary.processedTestCaseCount());
    }

    @Test
    @DisplayName("모든 실행이 성공하면 COMPLETED를 계산한다")
    void calculatesCompletedWhenEveryExecutionSucceeds() {
        TestRunExecutionSummary summary = TestRunExecutionSummary.from(List.of(
                status(1, TestExecutionStatus.SUCCEEDED)
        ));

        assertEquals(TestRunExecutionOutcome.COMPLETED, summary.outcome());
    }

    @Test
    @DisplayName("성공과 실패 또는 timeout이 섞이면 INCOMPLETE를 계산한다")
    void calculatesIncompleteWhenSuccessAndFailureAreMixed() {
        TestRunExecutionSummary summary = TestRunExecutionSummary.from(List.of(
                status(1, TestExecutionStatus.SUCCEEDED),
                status(2, TestExecutionStatus.TIMED_OUT)
        ));

        assertEquals(TestRunExecutionOutcome.INCOMPLETE, summary.outcome());
    }

    @Test
    @DisplayName("성공한 실행 결과가 없으면 ERROR를 계산한다")
    void calculatesErrorWhenNoExecutionSucceeds() {
        TestRunExecutionSummary summary = TestRunExecutionSummary.from(List.of(
                status(1, TestExecutionStatus.FAILED),
                status(2, TestExecutionStatus.TIMED_OUT)
        ));

        assertEquals(TestRunExecutionOutcome.ERROR, summary.outcome());
    }

    @Test
    @DisplayName("같은 Snapshot 실행을 중복 입력하면 거부한다")
    void rejectsDuplicateSnapshotExecutions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TestRunExecutionSummary.from(List.of(
                        status(1, TestExecutionStatus.SUCCEEDED),
                        status(1, TestExecutionStatus.SUCCEEDED)
                ))
        );
    }

    private static SnapshotExecutionStatus status(long snapshotId, TestExecutionStatus executionStatus) {
        return new SnapshotExecutionStatus(new TestCaseSnapshotId(snapshotId), executionStatus);
    }
}
