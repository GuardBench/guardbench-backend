package com.guardbench.testrun.presentation.dto;

public record TestRunComparisonSummaryRes(
        long currentRunId,
        long comparisonRunId,
        long totalCases,
        long changedCount,
        long unchangedCount,
        long improvedCount,
        long regressedCount,
        long notComparableCount) {
}
