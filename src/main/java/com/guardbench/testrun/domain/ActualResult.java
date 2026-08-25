package com.guardbench.testrun.domain;

import java.util.Objects;

public record ActualResult(Action action) {

    public ActualResult {
        Objects.requireNonNull(action, "action must not be null");
    }
}
