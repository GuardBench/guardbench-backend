package com.guardbench.testdefinition.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestCaseIdTest {

    @Test
    @DisplayName("양수 값으로 생성하면 그 값을 그대로 보유한다")
    void exposesPositiveValueUsedForCreation() {
        TestCaseId testCaseId = new TestCaseId(99L);

        assertEquals(99L, testCaseId.value());
    }

    @Test
    @DisplayName("같은 값을 가진 두 TestCaseId는 값으로 동등하다")
    void equalsAnotherTestCaseIdWithSameValue() {
        assertEquals(new TestCaseId(3L), new TestCaseId(3L));
    }

    @Test
    @DisplayName("값이 0이면 IllegalArgumentException을 던진다")
    void rejectsZeroValue() {
        assertThrows(IllegalArgumentException.class, () -> new TestCaseId(0L));
    }

    @Test
    @DisplayName("값이 음수면 IllegalArgumentException을 던진다")
    void rejectsNegativeValue() {
        assertThrows(IllegalArgumentException.class, () -> new TestCaseId(-5L));
    }
}
