package com.guardbench.testrun.presentation.dto;

public record ComparableTestRunListItemRes(
        long id,
        long testSuiteId,
        TargetReferenceRes target,
        String completedAt) {
}
