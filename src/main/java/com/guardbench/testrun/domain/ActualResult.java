package com.guardbench.testrun.domain;

import java.util.Objects;

import com.guardbench.testdefinition.domain.Action;

public record ActualResult(Action action) {

    public ActualResult {
        Objects.requireNonNull(action, "action must not be null");
    }

    public static ActualResult of(Action action) {
        return new ActualResult(action);
    }
}
