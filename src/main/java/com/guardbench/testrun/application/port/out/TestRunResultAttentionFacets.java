package com.guardbench.testrun.application.port.out;

public record TestRunResultAttentionFacets(
        long allResults,
        long falseNegative,
        long falsePositive,
        long executionFailed,
        long timedOut,
        long notStarted) {

    public TestRunResultAttentionFacets {
        if (allResults < 0 || falseNegative < 0 || falsePositive < 0
                || executionFailed < 0 || timedOut < 0 || notStarted < 0) {
            throw new IllegalArgumentException("attention facet counts must not be negative");
        }
    }

    public long attentionTotal() {
        return falseNegative + falsePositive + executionFailed + timedOut + notStarted;
    }
}
