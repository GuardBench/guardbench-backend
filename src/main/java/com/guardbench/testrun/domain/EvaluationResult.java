package com.guardbench.testrun.domain;

import java.util.Objects;

/** Evaluator가 Application response를 정규화한 GuardBench 공통 verdict다. */
public record EvaluationResult(Action action) {

    public EvaluationResult {
        Objects.requireNonNull(action, "evaluation action must not be null");
    }
}
