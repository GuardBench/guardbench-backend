package com.guardbench.testrun.presentation.dto;

/** Case-level 항목을 제외한 완료 TestRun 저장 결과 비교 집계다. */
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
