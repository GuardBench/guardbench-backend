package com.guardbench.testrun.application.port.out;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GuardrailPortContractTest {

    @Test
    @DisplayName("materialization 요청은 TestRun별 결정적 clientRequestToken을 생성한다")
    void createsDeterministicClientRequestToken() {
        GuardrailMaterializationRequest request = new GuardrailMaterializationRequest("gr-123", 42);

        assertEquals("guardbench-test-run-42", request.clientRequestToken());
    }

    @Test
    @DisplayName("materialized version은 숫자형 확정 version만 허용한다")
    void acceptsOnlyResolvedNumericVersion() {
        GuardrailMaterializedVersion version = new GuardrailMaterializedVersion("gr-123", "7");

        assertEquals("7", version.version());
    }

    @Test
    @DisplayName("execution 결과는 action 또는 failure 중 하나만 표현한다")
    void representsProviderResultAsActionOrFailure() {
        GuardrailExecutionResult success = GuardrailExecutionResult.succeeded("NONE");
        GuardrailExecutionResult failure = GuardrailExecutionResult.failed(GuardrailFailureCode.PROVIDER_UNAVAILABLE);

        assertEquals("NONE", success.actionCode());
        assertEquals(GuardrailFailureCode.PROVIDER_UNAVAILABLE, failure.failureCode());
    }
}
