package com.guardbench.testdefinition.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;

class TestCaseBulkCreateFingerprintTest {

    @Test
    @DisplayName("같은 Suite와 순서가 같은 TestCase 요청은 동일한 SHA-256 fingerprint를 만든다")
    void sameOrderedRequestProducesSameFingerprint() {
        TestCaseBulkCreateCommand first = command(List.of(item("첫째"), item("둘째")));
        TestCaseBulkCreateCommand second = command(List.of(item("첫째"), item("둘째")));

        String firstFingerprint = TestCaseBulkCreateFingerprint.of(10L, first);
        String secondFingerprint = TestCaseBulkCreateFingerprint.of(10L, second);

        assertEquals(firstFingerprint, secondFingerprint);
        assertEquals(64, firstFingerprint.length());
    }

    @Test
    @DisplayName("Suite, 항목 순서 또는 정의가 달라지면 fingerprint도 달라진다")
    void requestIdentityChangesFingerprint() {
        TestCaseBulkCreateCommand original = command(List.of(item("첫째"), item("둘째")));

        assertNotEquals(
                TestCaseBulkCreateFingerprint.of(10L, original),
                TestCaseBulkCreateFingerprint.of(11L, original));
        assertNotEquals(
                TestCaseBulkCreateFingerprint.of(10L, original),
                TestCaseBulkCreateFingerprint.of(
                        10L, command(List.of(item("둘째"), item("첫째")))));
        assertNotEquals(
                TestCaseBulkCreateFingerprint.of(10L, original),
                TestCaseBulkCreateFingerprint.of(
                        10L, command(List.of(item("첫째"), item("변경")))));
    }

    private static TestCaseBulkCreateCommand command(List<TestCaseCreateCommand> items) {
        return new TestCaseBulkCreateCommand("key", items);
    }

    private static TestCaseCreateCommand item(String name) {
        return new TestCaseCreateCommand(
                name, "입력", Action.BLOCK, Severity.HIGH, "PROMPT_INJECTION");
    }
}
