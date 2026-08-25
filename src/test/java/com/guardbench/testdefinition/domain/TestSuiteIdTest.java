package com.guardbench.testdefinition.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestSuiteIdTest {

    @Test
    @DisplayName("양수 값으로 생성하면 그 값을 그대로 보유한다")
    void exposesPositiveValueUsedForCreation() {
        TestSuiteId testSuiteId = new TestSuiteId(42L);

        assertEquals(42L, testSuiteId.value());
    }

    @Test
    @DisplayName("같은 값을 가진 두 TestSuiteId는 값으로 동등하다")
    void equalsAnotherTestSuiteIdWithSameValue() {
        assertEquals(new TestSuiteId(7L), new TestSuiteId(7L));
    }

    @Test
    @DisplayName("값이 0이면 IllegalArgumentException을 던진다")
    void rejectsZeroValue() {
        assertThrows(IllegalArgumentException.class, () -> new TestSuiteId(0L));
    }

    @Test
    @DisplayName("값이 음수면 IllegalArgumentException을 던진다")
    void rejectsNegativeValue() {
        assertThrows(IllegalArgumentException.class, () -> new TestSuiteId(-1L));
    }

    @Test
    @DisplayName("같은 값이어도 TestCaseId와는 동등하지 않다")
    void isNotEqualToTestCaseIdWithSameValue() {
        assertNotEquals(new TestCaseId(1L), new TestSuiteId(1L));
    }
}
