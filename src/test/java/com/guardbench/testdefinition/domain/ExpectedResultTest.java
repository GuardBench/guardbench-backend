package com.guardbench.testdefinition.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExpectedResultTest {

    @Test
    @DisplayName("생성에 사용한 action을 그대로 보유한다")
    void exposesActionUsedForCreation() {
        ExpectedResult expectedResult = new ExpectedResult(Action.BLOCK);

        assertEquals(Action.BLOCK, expectedResult.action());
    }

    @Test
    @DisplayName("action이 null이면 IllegalArgumentException을 던진다")
    void rejectsNullAction() {
        assertThrows(IllegalArgumentException.class, () -> new ExpectedResult(null));
    }

    @Test
    @DisplayName("같은 action을 가진 두 ExpectedResult는 값으로 동등하다")
    void equalsAnotherExpectedResultWithSameAction() {
        ExpectedResult expectedResult = new ExpectedResult(Action.ALLOW);
        ExpectedResult sameValue = new ExpectedResult(Action.ALLOW);

        assertEquals(sameValue, expectedResult);
    }
}
