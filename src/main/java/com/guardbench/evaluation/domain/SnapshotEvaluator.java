package com.guardbench.evaluation.domain;

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
            EvaluationAction baselineAction,
            EvaluationAction candidateAction,
            boolean comparisonConditionsSatisfied) {
        Objects.requireNonNull(reference, "Snapshot evaluation reference must not be null");
        Objects.requireNonNull(expectedAction, "Expected action must not be null");

        if (candidateAction == null) {
            return Optional.empty();
        }

        AssertionResult assertion = AssertionResult.evaluate(expectedAction, candidateAction);
        ChangeResult change = evaluateChange(
                expectedAction,
                baselineAction,
                candidateAction,
                comparisonConditionsSatisfied);

        return Optional.of(new SnapshotEvaluation(reference, assertion, change));
    }

    private ChangeResult evaluateChange(
            EvaluationAction expectedAction,
            EvaluationAction baselineAction,
            EvaluationAction candidateAction,
            boolean comparisonConditionsSatisfied) {
        if (baselineAction == null) {
            return null;
        }
        if (!comparisonConditionsSatisfied) {
            return ChangeResult.notComparable();
        }
        if (baselineAction == candidateAction) {
            return ChangeResult.comparable(ChangeType.NO_CHANGE);
        }
        if (candidateAction == expectedAction) {
            return ChangeResult.comparable(ChangeType.IMPROVEMENT);
        }
        if (expectedAction == EvaluationAction.BLOCK) {
            return ChangeResult.comparable(ChangeType.SECURITY_REGRESSION);
        }

        return ChangeResult.comparable(ChangeType.USABILITY_REGRESSION);
    }
}
