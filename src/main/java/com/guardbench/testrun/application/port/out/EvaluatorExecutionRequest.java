package com.guardbench.testrun.application.port.out;

import java.util.Objects;

import com.guardbench.testrun.domain.EvaluatorReference;

/**
 * Response Behavior Classifier가 평가할 (prompt, Application 자연어 응답)과 immutable Evaluator
 * reference다.
 *
 * <p>{@code prompt}는 TestCaseSnapshot의 input이다. Classifier가 partial refusal/compliance에서
 * 핵심 요청 수행 여부를 판별하려면 원 요청이 필요하므로 응답만 전달하지 않는다.
 */
public record EvaluatorExecutionRequest(
        EvaluatorReference evaluatorReference,
        String prompt,
        String applicationResponse
) {

    public EvaluatorExecutionRequest {
        Objects.requireNonNull(evaluatorReference, "evaluator reference must not be null");
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (applicationResponse == null || applicationResponse.isBlank()) {
            throw new IllegalArgumentException("application response must not be blank");
        }
    }
}
