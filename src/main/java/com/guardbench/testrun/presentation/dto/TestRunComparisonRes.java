package com.guardbench.testrun.presentation.dto;

import java.util.List;

public record TestRunComparisonRes(
        long currentRunId,
        long comparisonRunId,
        long totalCases,
        long changedCount,
        long unchangedCount,
        long improvedCount,
        long regressedCount,
        long notComparableCount,
        List<TestRunComparisonItemRes> items) {
}
