package com.guardbench.evaluation.domain;

import java.util.Objects;

public record ChangeResult(
        ComparabilityStatus comparabilityStatus,
        ChangeType changeType) {

    public ChangeResult {
        Objects.requireNonNull(comparabilityStatus, "Comparability status must not be null");
        if (comparabilityStatus == ComparabilityStatus.COMPARABLE && changeType == null) {
            throw new IllegalArgumentException("Comparable change result requires a change type");
        }
        if (comparabilityStatus == ComparabilityStatus.NOT_COMPARABLE && changeType != null) {
            throw new IllegalArgumentException("Not comparable change result cannot have a change type");
        }
    }

    static ChangeResult comparable(ChangeType changeType) {
        return new ChangeResult(
                ComparabilityStatus.COMPARABLE,
                Objects.requireNonNull(changeType, "Change type must not be null"));
    }

    static ChangeResult notComparable() {
        return new ChangeResult(ComparabilityStatus.NOT_COMPARABLE, null);
    }
}
