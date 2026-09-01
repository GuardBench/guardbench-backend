package com.guardbench.evaluation.domain;

import java.util.Objects;

/** 하나의 동일 TestCase에 대한 비교 판정이다. */
public record StoredRegressionChange(long testCaseId, ChangeResult result) {

    public StoredRegressionChange {
        if (testCaseId <= 0) {
            throw new IllegalArgumentException("TestCase ID must be positive");
        }
        Objects.requireNonNull(result, "Change result must not be null");
    }
}
