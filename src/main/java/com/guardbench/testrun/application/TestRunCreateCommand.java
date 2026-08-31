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
        String targetModel,
        com.guardbench.testrun.domain.EvaluationProfile evaluationProfile,
        String idempotencyKey
) {

    public TestRunCreateCommand {
        Objects.requireNonNull(targetType, "targetType must not be null");
        Objects.requireNonNull(targetIdentifier, "targetIdentifier must not be null");
        if (targetModel == null || targetModel.isBlank()) {
            throw new IllegalArgumentException("targetModel must not be blank");
        }
    }

    /**
     * Legacy source compatibility only. HTTP_ENDPOINT 신규 요청은 model이 필수이므로 호출 시 검증에 실패한다.
     */
    @Deprecated
    public TestRunCreateCommand(long testSuiteId, String targetType, String targetIdentifier, String targetRevision, String idempotencyKey) {
        this(testSuiteId, targetType, targetIdentifier, targetRevision, null, null, idempotencyKey);
    }

    /**
     * Legacy source compatibility only. HTTP_ENDPOINT 신규 요청은 model이 필수이므로 호출 시 검증에 실패한다.
     */
    @Deprecated
    public TestRunCreateCommand(long testSuiteId, String targetType, String targetIdentifier, String targetRevision,
                                com.guardbench.testrun.domain.EvaluationProfile evaluationProfile, String idempotencyKey) {
        this(testSuiteId, targetType, targetIdentifier, targetRevision, null, evaluationProfile, idempotencyKey);
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
                evaluationProfile
        );
    }
}
