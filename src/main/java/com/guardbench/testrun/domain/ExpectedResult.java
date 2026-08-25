package com.guardbench.testrun.domain;

import java.util.Objects;

public record ExpectedResult(Action action) {

    public ExpectedResult {
        Objects.requireNonNull(action, "action must not be null");
    }
}
