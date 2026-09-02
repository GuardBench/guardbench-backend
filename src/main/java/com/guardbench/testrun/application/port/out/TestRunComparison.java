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

        public TestRunComparisonItem {
            boolean comparable = "COMPARABLE".equals(comparabilityStatus);
            boolean notComparable = "NOT_COMPARABLE".equals(comparabilityStatus);
            if (!comparable && !notComparable) {
                throw new IllegalArgumentException("Unknown comparison status");
            }
            if (comparable && (comparisonVerdict == null || currentVerdict == null || changeType == null)) {
                throw new IllegalArgumentException("Comparable item requires verdicts and change type");
            }
            if (notComparable && ((comparisonVerdict != null && currentVerdict != null) || changeType != null)) {
                throw new IllegalArgumentException("Not-comparable item requires a missing verdict and null change type");
            }
        }
    }
}
