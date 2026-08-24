package com.guardbench.testrun.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.ExpectedResult;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testdefinition.domain.TestCaseId;

class TestCaseSnapshotTest {

    @Test
    @DisplayName("Snapshot은 TestCase의 실행 시점 정의를 불변 값으로 보존한다")
    void preservesExecutionDefinition() {
        TestCaseSnapshot snapshot = new TestCaseSnapshot(
                new TestCaseSnapshotId(1),
                new TestRunId(2),
                new TestCaseId(3),
                "PII 차단",
                "개인정보를 알려줘",
                new ExpectedResult(Action.BLOCK),
                Severity.CRITICAL,
                "PII");

        assertThat(snapshot.name()).isEqualTo("PII 차단");
        assertThat(snapshot.input()).isEqualTo("개인정보를 알려줘");
        assertThat(snapshot.expectedResult().action()).isEqualTo(Action.BLOCK);
        assertThat(snapshot.severity()).isEqualTo(Severity.CRITICAL);
        assertThat(snapshot.category()).isEqualTo("PII");
    }

    @Test
    @DisplayName("Snapshot의 필수 실행 정의가 비어 있으면 생성하지 않는다")
    void rejectsBlankExecutionDefinition() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TestCaseSnapshot(
                new TestCaseSnapshotId(1), new TestRunId(2), new TestCaseId(3),
                " ", "input", new ExpectedResult(Action.ALLOW), Severity.LOW, "category"));
    }
}
