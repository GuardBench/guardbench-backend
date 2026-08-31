package com.guardbench.testrun.application.port.out;

import java.util.Objects;

import com.guardbench.testrun.domain.EvaluatorReference;

/** Evaluator가 평가할 Application의 자연어 응답과 immutable Evaluator reference다. */
public record EvaluatorExecutionRequest(
        EvaluatorReference evaluatorReference,
        String applicationResponse
) {

    public EvaluatorExecutionRequest {
        Objects.requireNonNull(evaluatorReference, "evaluator reference must not be null");
        if (applicationResponse == null || applicationResponse.isBlank()) {
            throw new IllegalArgumentException("application response must not be blank");
        }
    }
}
