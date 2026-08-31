package com.guardbench.testrun.domain;

/** TestRun이 실제 사용한 Evaluator 설정과 immutable revision의 local reference다. */
public record EvaluatorReference(String value) {
    public EvaluatorReference {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("evaluator reference must not be blank");
        }
    }
}
