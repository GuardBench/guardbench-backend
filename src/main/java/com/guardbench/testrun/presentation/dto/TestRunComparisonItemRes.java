package com.guardbench.testrun.presentation.dto;

public record TestRunComparisonItemRes(
        long snapshotId,
        long testCaseId,
        String name,
        String input,
        String expectedAction,
        String comparisonVerdict,
        String currentVerdict,
        String comparabilityStatus,
        String changeType) {
}
