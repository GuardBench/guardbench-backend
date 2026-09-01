package com.guardbench.testrun.application.port.out;

import java.util.List;

import com.guardbench.testrun.domain.Action;

public record TestRunComparison(
        long currentRunId,
        long comparisonRunId,
        long totalCases,
        long changedCount,
        long unchangedCount,
        long improvedCount,
        long regressedCount,
        long notComparableCount,
        List<TestRunComparisonItem> items) {

    public TestRunComparison {
        if (currentRunId <= 0 || comparisonRunId <= 0 || totalCases < 0
                || changedCount < 0 || unchangedCount < 0 || improvedCount < 0
                || regressedCount < 0 || notComparableCount < 0) {
            throw new IllegalArgumentException("Invalid TestRun comparison summary");
        }
        items = List.copyOf(items);
    }

    public record TestRunComparisonItem(
            long snapshotId,
            long testCaseId,
            String name,
            String input,
            Action expectedAction,
            Action comparisonVerdict,
            Action currentVerdict,
            String comparabilityStatus,
            String changeType) {
    }
}
