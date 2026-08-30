package com.guardbench.evaluation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Binary Action만 사용하는 MVP Snapshot 평가기다.
 *
 * <p>입력 Action과 reference는 Evaluation Context가 소유하며 TestDefinition 또는 TestRun의
 * Domain Java 타입을 직접 사용하지 않는다.
 */
public final class SnapshotEvaluator {

    public Optional<SnapshotEvaluation> evaluate(
            SnapshotEvaluationReference reference,
            EvaluationAction expectedAction,
            EvaluationAction actualAction,
            Instant createdAt) {
        Objects.requireNonNull(reference, "Snapshot evaluation reference must not be null");
        Objects.requireNonNull(expectedAction, "Expected action must not be null");
        Objects.requireNonNull(createdAt, "Snapshot evaluation createdAt must not be null");

        if (actualAction == null) {
            return Optional.empty();
        }

        AssertionResult assertion = AssertionResult.evaluate(expectedAction, actualAction);

        return Optional.of(new SnapshotEvaluation(reference, assertion, null, createdAt));
    }
}
