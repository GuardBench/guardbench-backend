package com.guardbench.testrun.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.guardbench.testdefinition.domain.Action;

class TestExecutionTest {

    private static final TestExecutionId ID = new TestExecutionId(new TestCaseSnapshotId(1), TargetType.CANDIDATE);
    private static final Instant STARTED_AT = Instant.parse("2026-08-25T00:00:00Z");
    private static final Instant COMPLETED_AT = STARTED_AT.plusSeconds(1);

    @Test
    @DisplayName("SUCCEEDED 실행만 ActualResult를 가진다")
    void succeededExecutionHasActualResult() {
        TestExecution execution = TestExecution.succeeded(
                ID, new ActualResult(Action.BLOCK), STARTED_AT, COMPLETED_AT);

        assertThat(execution.status()).isEqualTo(TestExecutionResultStatus.SUCCEEDED);
        assertThat(execution.actualResult().action()).isEqualTo(Action.BLOCK);
        assertThat(execution.error()).isNull();
    }

    @Test
    @DisplayName("NOT_STARTED 실행은 결과와 시각을 갖지 않는다")
    void notStartedExecutionHasNoResultOrTimestamps() {
        TestExecution execution = TestExecution.notStarted(ID);

        assertThat(execution.status()).isEqualTo(TestExecutionResultStatus.NOT_STARTED);
        assertThat(execution.actualResult()).isNull();
        assertThat(execution.startedAt()).isNull();
        assertThat(execution.completedAt()).isNull();
    }

    @Test
    @DisplayName("실행 완료 시각이 시작 시각보다 빠르면 생성하지 않는다")
    void rejectsReversedTimestamps() {
        assertThatIllegalArgumentException().isThrownBy(() -> TestExecution.succeeded(
                ID, new ActualResult(Action.ALLOW), COMPLETED_AT, STARTED_AT));
    }
}
