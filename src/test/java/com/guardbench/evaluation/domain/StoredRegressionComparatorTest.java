package com.guardbench.evaluation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

class StoredRegressionComparatorTest {

    private final StoredRegressionComparator comparator = new StoredRegressionComparator();

    @Test
    void classifiesAllBinaryTransitionsUsingExpectedAction() {
        StoredRegressionComparison result = comparator.compare(List.of(
                new StoredRegressionCase(1L, EvaluationAction.ALLOW, EvaluationAction.ALLOW, EvaluationAction.ALLOW),
                new StoredRegressionCase(2L, EvaluationAction.ALLOW, EvaluationAction.BLOCK, EvaluationAction.ALLOW),
                new StoredRegressionCase(3L, EvaluationAction.BLOCK, EvaluationAction.ALLOW, EvaluationAction.BLOCK),
                new StoredRegressionCase(4L, EvaluationAction.BLOCK, EvaluationAction.BLOCK, EvaluationAction.ALLOW),
                new StoredRegressionCase(5L, EvaluationAction.ALLOW, EvaluationAction.ALLOW, EvaluationAction.BLOCK)));

        assertEquals(List.of(ChangeType.NO_CHANGE, ChangeType.IMPROVEMENT,
                        ChangeType.IMPROVEMENT, ChangeType.SECURITY_REGRESSION,
                        ChangeType.USABILITY_REGRESSION),
                result.changes().stream().map(change -> change.result().changeType()).toList());
    }

    @Test
    void missingStoredVerdictIsNotComparable() {
        StoredRegressionComparison result = comparator.compare(List.of(
                new StoredRegressionCase(1L, EvaluationAction.ALLOW, null, EvaluationAction.ALLOW)));

        assertEquals(ComparabilityStatus.NOT_COMPARABLE, result.changes().getFirst().result().comparabilityStatus());
        assertEquals(null, result.changes().getFirst().result().changeType());
    }
}
