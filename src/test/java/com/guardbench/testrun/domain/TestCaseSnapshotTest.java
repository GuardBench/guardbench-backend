package com.guardbench.testrun.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.guardbench.testrun.application.TestCaseSnapshotSourceMapper;
import com.guardbench.testrun.application.port.out.TestCaseSnapshotSource;

class TestCaseSnapshotTest {

    @Test
    @DisplayName("Snapshot은 source의 실행 시점 값을 testrun Context 소유 값으로 복제한다")
    void snapshotCopiesSourceValuesIntoTestRunLocalModel() {
        TestCaseSnapshotSource source = new TestCaseSnapshotSource(
                10,
                20,
                "PII 차단",
                "주민등록번호는 123456-1234567입니다.",
                "BLOCK",
                "CRITICAL",
                "PII"
        );

        TestCaseSnapshot snapshot = TestCaseSnapshotSourceMapper.toSnapshot(
                source,
                new TestCaseSnapshotId(30),
                new TestRunId(40)
        );

        assertEquals(new SourceTestCaseId(20), snapshot.sourceTestCaseId());
        assertEquals("PII 차단", snapshot.name());
        assertEquals(new ExpectedResult(Action.BLOCK), snapshot.expectedResult());
        assertEquals(Severity.CRITICAL, snapshot.severity());
        assertEquals("PII", snapshot.category());
    }

    @Test
    @DisplayName("Snapshot은 필수 실행 정의가 비어 있으면 생성할 수 없다")
    void rejectsBlankExecutionDefinition() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TestCaseSnapshot.of(
                        new TestCaseSnapshotId(1),
                        new TestRunId(2),
                        new SourceTestCaseId(3),
                        "name",
                        "input",
                        new ExpectedResult(Action.ALLOW),
                        Severity.HIGH,
                        " "
                )
        );
    }
}
