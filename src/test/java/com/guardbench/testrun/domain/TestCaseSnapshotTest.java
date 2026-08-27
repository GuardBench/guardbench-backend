package com.guardbench.testrun.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.guardbench.testrun.application.TestCaseSnapshotSourceMapper;
import com.guardbench.testrun.application.port.out.TestCaseSnapshotSource;

class TestCaseSnapshotTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    @DisplayName("Snapshot은 source의 실행 시점 값과 Application Clock 생성 시각을 testrun Context 값으로 복제한다")
    void snapshotCopiesSourceValuesAndApplicationCreatedTimeIntoTestRunLocalModel() {
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
                new TestRunId(40),
                CREATED_AT
        );

        assertEquals(new SourceTestCaseId(20), snapshot.sourceTestCaseId());
        assertEquals("PII 차단", snapshot.name());
        assertEquals(new ExpectedResult(Action.BLOCK), snapshot.expectedResult());
        assertEquals(Severity.CRITICAL, snapshot.severity());
        assertEquals("PII", snapshot.category());
        assertEquals(CREATED_AT, snapshot.createdAt());
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
                        " ",
                        CREATED_AT
                )
        );
    }

    @Test
    @DisplayName("Snapshot은 Application Clock 생성 시각 없이는 생성할 수 없다")
    void rejectsSnapshotWithoutApplicationCreatedTime() {
        assertThrows(
                NullPointerException.class,
                () -> TestCaseSnapshot.of(
                        new TestCaseSnapshotId(1),
                        new TestRunId(2),
                        new SourceTestCaseId(3),
                        "name",
                        "input",
                        new ExpectedResult(Action.ALLOW),
                        Severity.HIGH,
                        "PII",
                        null
                )
        );
    }
}
