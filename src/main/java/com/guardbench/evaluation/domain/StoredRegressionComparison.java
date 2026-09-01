package com.guardbench.evaluation.domain;

import java.util.List;
import java.util.Objects;

/** 저장된 결과만으로 계산한 Snapshot별 Regression 변화다. */
public record StoredRegressionComparison(List<StoredRegressionChange> changes) {

    public StoredRegressionComparison {
        Objects.requireNonNull(changes, "Regression changes must not be null");
        changes = List.copyOf(changes);
    }
}
