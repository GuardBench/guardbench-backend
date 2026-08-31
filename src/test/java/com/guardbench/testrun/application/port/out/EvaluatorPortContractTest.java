package com.guardbench.testrun.application.port.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.guardbench.testrun.domain.EvaluatorReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluatorPortContractTest {

    @Test
    @DisplayName("Evaluator 요청은 reference와 자연어 response를 요구한다")
    void requestRequiresReferenceAndApplicationResponse() {
        EvaluatorExecutionRequest request = new EvaluatorExecutionRequest(
                new EvaluatorReference("evaluator-ref"), "safe response");

        assertEquals("evaluator-ref", request.evaluatorReference().value());
        assertEquals("safe response", request.applicationResponse());
        assertThrows(IllegalArgumentException.class,
                () -> new EvaluatorExecutionRequest(new EvaluatorReference("evaluator-ref"), " "));
    }

    @Test
    @DisplayName("Evaluator 결과는 action 또는 failure 중 하나만 가진다")
    void resultRequiresExactlyOneOutcome() {
        EvaluatorExecutionResult success = EvaluatorExecutionResult.succeeded("ALLOW");
        EvaluatorExecutionResult failure = EvaluatorExecutionResult.failed(EvaluatorFailureCode.PROVIDER_TIMEOUT);

        assertEquals("ALLOW", success.actionCode());
        assertEquals(EvaluatorFailureCode.PROVIDER_TIMEOUT, failure.failureCode());
        assertThrows(IllegalArgumentException.class,
                () -> new EvaluatorExecutionResult("ALLOW", EvaluatorFailureCode.PROVIDER_TIMEOUT));
    }
}
