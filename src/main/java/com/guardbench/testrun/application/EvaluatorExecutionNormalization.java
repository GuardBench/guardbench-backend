package com.guardbench.testrun.application;

import com.guardbench.testrun.domain.EvaluationResult;
import com.guardbench.testrun.domain.TestExecutionError;

/** Evaluator Port 결과를 TestRun 실행 결과로 정규화한 값이다. */
public record EvaluatorExecutionNormalization(EvaluationResult evaluationResult, TestExecutionError error) {

    public EvaluatorExecutionNormalization {
        if ((evaluationResult == null) == (error == null)) {
            throw new IllegalArgumentException("normalization must contain exactly one result or error");
        }
    }

    public static EvaluatorExecutionNormalization succeeded(EvaluationResult evaluationResult) {
        return new EvaluatorExecutionNormalization(evaluationResult, null);
    }

    public static EvaluatorExecutionNormalization failed(TestExecutionError error) {
        return new EvaluatorExecutionNormalization(null, error);
    }

    public boolean isSuccess() {
        return evaluationResult != null;
    }
}
