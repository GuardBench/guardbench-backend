package com.guardbench.testdefinition.domain;

import java.util.Objects;

public record ExpectedResult(Action action) {

    public ExpectedResult {
        Objects.requireNonNull(action, "action must not be null");
    }

    public static ExpectedResult of(Action action) {
        return new ExpectedResult(action);
    }
}
