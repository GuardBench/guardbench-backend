package com.guardbench.testrun.application;

import java.util.Objects;

/**
 * TestRun 접수 유스케이스의 입력이다. Presentation 계층이 조립한다.
 */
public record TestRunCreateCommand(
        long testSuiteId,
        String targetType,
        String targetIdentifier,
        String targetRevision,
        com.guardbench.testrun.domain.EvaluationProfile evaluationProfile,
        String idempotencyKey
) {

    public TestRunCreateCommand {
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier must not be null");
    }

    public TestRunCreateCommand(long testSuiteId, String targetType, String targetIdentifier, String targetRevision, String idempotencyKey) {
        this(testSuiteId, targetType, targetIdentifier, targetRevision, null, idempotencyKey);
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
                evaluationProfile
        );
    }
}
