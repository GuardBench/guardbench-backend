package com.guardbench.evaluation.infrastructure.integration;

import java.util.Objects;

import com.guardbench.evaluation.application.port.out.AiServiceActionPort;
import com.guardbench.evaluation.domain.EvaluationAction;
import com.guardbench.testrun.application.AiServiceExecutionFacade;

/**
 * Evaluation의 AI service action Port를 TestRun Application Facade에 연결한다.
 */
public final class AiServiceActionIntegrationAdapter implements AiServiceActionPort {

    private final AiServiceExecutionFacade executionFacade;

    public AiServiceActionIntegrationAdapter(AiServiceExecutionFacade executionFacade) {
        this.executionFacade = Objects.requireNonNull(executionFacade, "executionFacade must not be null");
    }

    @Override
    public EvaluationAction execute(String endpoint, String input) {
        return EvaluationAction.valueOf(executionFacade.execute(endpoint, input));
    }
}
