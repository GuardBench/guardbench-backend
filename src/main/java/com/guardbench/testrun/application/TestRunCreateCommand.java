package com.guardbench.testrun.application;

import java.util.Objects;

import com.guardbench.testrun.domain.QualityGatePolicy;

/**
 * TestRun 접수 유스케이스의 입력이다. Presentation 계층이 조립한다.
 */
public record TestRunCreateCommand(
        long testSuiteId,
        String targetType,
        String targetIdentifier,
        String targetRevision,
        String targetModel,
        double assertionPassRateThreshold,
        double executionSuccessRateThreshold,
        String idempotencyKey
) {

    public TestRunCreateCommand(
            long testSuiteId,
            String targetType,
            String targetIdentifier,
            String targetRevision,
            String targetModel,
            String idempotencyKey) {
        this(
                testSuiteId,
                targetType,
                targetIdentifier,
                targetRevision,
                targetModel,
                QualityGatePolicy.DEFAULT_ASSERTION_PASS_RATE_THRESHOLD,
                QualityGatePolicy.DEFAULT_EXECUTION_SUCCESS_RATE_THRESHOLD,
                idempotencyKey);
    }

    public TestRunCreateCommand {
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier must not be null");
        if (targetModel == null || targetModel.isBlank()) {
            throw new IllegalArgumentException("targetModel must not be blank");
        }
        new QualityGatePolicy(assertionPassRateThreshold, executionSuccessRateThreshold);
    }

    boolean hasIdempotencyKey() {
        return idempotencyKey != null;
    }

    TestRunCreateIntent toIntent() {
        return new TestRunCreateIntent(
                testSuiteId,
                targetType,
                targetIdentifier,
                targetRevision,
                targetModel,
                assertionPassRateThreshold,
                executionSuccessRateThreshold
        );
    }
}
