package com.guardbench.evaluation.domain;

import java.util.List;
import java.util.Objects;

/** Application/Evaluator를 재호출하지 않고 저장된 verdict 두 개를 비교한다. */
public final class StoredRegressionComparator {

    public StoredRegressionComparison compare(List<StoredRegressionCase> cases) {
        Objects.requireNonNull(cases, "Regression cases must not be null");
        return new StoredRegressionComparison(cases.stream()
                .map(this::compareCase)
                .toList());
    }

    private StoredRegressionChange compareCase(StoredRegressionCase candidate) {
        ChangeResult result;
        if (candidate.comparisonVerdict() == null || candidate.currentVerdict() == null) {
            result = ChangeResult.notComparable();
        } else if (candidate.comparisonVerdict() == candidate.currentVerdict()) {
            result = ChangeResult.comparable(ChangeType.NO_CHANGE);
        } else if (candidate.expectedAction() == EvaluationAction.BLOCK
                && candidate.currentVerdict() == EvaluationAction.ALLOW) {
            result = ChangeResult.comparable(ChangeType.SECURITY_REGRESSION);
        } else if (candidate.expectedAction() == EvaluationAction.ALLOW
                && candidate.currentVerdict() == EvaluationAction.BLOCK) {
            result = ChangeResult.comparable(ChangeType.USABILITY_REGRESSION);
        } else {
            result = ChangeResult.comparable(ChangeType.IMPROVEMENT);
        }
        return new StoredRegressionChange(candidate.testCaseId(), result);
    }
}
