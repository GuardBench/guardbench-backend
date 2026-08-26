package com.guardbench.testrun.application;

import java.util.Objects;

/**
 * TestRun 접수 유스케이스의 입력이다. Presentation 계층이 조립한다.
 */
public record TestRunCreateCommand(
        long testSuiteId,
        String baselineGuardrailId,
        String baselineVersion,
        String candidateGuardrailId,
        String candidateSource,
        String idempotencyKey
) {

    public TestRunCreateCommand {
        Objects.requireNonNull(baselineGuardrailId, "baselineGuardrailId must not be null");
        Objects.requireNonNull(baselineVersion, "baselineVersion must not be null");
        Objects.requireNonNull(candidateGuardrailId, "candidateGuardrailId must not be null");
        Objects.requireNonNull(candidateSource, "candidateSource must not be null");
    }

    boolean hasIdempotencyKey() {
        return idempotencyKey != null;
    }

    TestRunCreateIntent toIntent() {
        return new TestRunCreateIntent(
                testSuiteId,
                baselineGuardrailId,
                baselineVersion,
                candidateGuardrailId,
                candidateSource
        );
    }
}
