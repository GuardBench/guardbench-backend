package com.guardbench.testrun.application.port.out;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guardbench.testrun.domain.Action;

import org.junit.jupiter.api.Test;

class TestRunComparisonTest {

    @Test
    void acceptsComparableItemWithBothVerdictsAndChangeType() {
        assertDoesNotThrow(() -> item(Action.ALLOW, Action.BLOCK, "COMPARABLE", "IMPROVEMENT"));
    }

    @Test
    void rejectsComparableItemWithMissingComparisonData() {
        assertThrows(IllegalArgumentException.class,
                () -> item(null, Action.BLOCK, "COMPARABLE", "IMPROVEMENT"));
        assertThrows(IllegalArgumentException.class,
                () -> item(Action.ALLOW, null, "COMPARABLE", "IMPROVEMENT"));
        assertThrows(IllegalArgumentException.class,
                () -> item(Action.ALLOW, Action.BLOCK, "COMPARABLE", null));
    }

    @Test
    void acceptsNotComparableItemWithOneOrBothVerdictsMissing() {
        assertDoesNotThrow(() -> item(null, Action.BLOCK, "NOT_COMPARABLE", null));
        assertDoesNotThrow(() -> item(Action.ALLOW, null, "NOT_COMPARABLE", null));
        assertDoesNotThrow(() -> item(null, null, "NOT_COMPARABLE", null));
    }

    @Test
    void rejectsNotComparableItemWithBothVerdictsOrChangeType() {
        assertThrows(IllegalArgumentException.class,
                () -> item(Action.ALLOW, Action.BLOCK, "NOT_COMPARABLE", null));
        assertThrows(IllegalArgumentException.class,
                () -> item(Action.ALLOW, null, "NOT_COMPARABLE", "NO_CHANGE"));
    }

    @Test
    void rejectsUnknownComparabilityStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> item(Action.ALLOW, Action.BLOCK, "UNKNOWN", "NO_CHANGE"));
    }

    private static TestRunComparison.TestRunComparisonItem item(
            Action comparisonVerdict,
            Action currentVerdict,
            String comparabilityStatus,
            String changeType) {
        return new TestRunComparison.TestRunComparisonItem(
                1L, 1L, "case", "input", Action.BLOCK,
                comparisonVerdict, currentVerdict, comparabilityStatus, changeType);
    }
}
