package com.guardbench.testrun.application.port.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GuardrailPortContractTest {

    private static final String GUARDRAIL_IDENTIFIER = "gr123";

    @Nested
    @DisplayName("Candidate materialization 요청")
    class MaterializationRequestTest {

        @Test
        @DisplayName("materialization 요청은 TestRun별 결정적 clientRequestToken을 생성한다")
        void createsDeterministicClientRequestToken() {
            GuardrailMaterializationRequest request =
                    new GuardrailMaterializationRequest(GUARDRAIL_IDENTIFIER, 42);

            assertEquals("guardbench-test-run-42", request.clientRequestToken());
        }

        @Test
        @DisplayName("빈 guardrail identifier는 거부된다")
        void rejectsBlankGuardrailIdentifier() {
            assertThrows(IllegalArgumentException.class,
                    () -> new GuardrailMaterializationRequest("   ", 42));
        }

        @Test
        @DisplayName("양수가 아닌 testRunId는 거부된다")
        void rejectsNonPositiveTestRunId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new GuardrailMaterializationRequest(GUARDRAIL_IDENTIFIER, 0));
        }
    }

    @Nested
    @DisplayName("Candidate materialization 결과 version")
    class MaterializedVersionTest {

        @Test
        @DisplayName("숫자형 확정 version은 그대로 노출된다")
        void exposesResolvedNumericVersion() {
            GuardrailMaterializedVersion version =
                    new GuardrailMaterializedVersion(GUARDRAIL_IDENTIFIER, "7");

            assertEquals("7", version.version());
        }

        @Test
        @DisplayName("DRAFT는 확정 version이 아니므로 거부된다")
        void rejectsDraftVersion() {
            assertThrows(IllegalArgumentException.class,
                    () -> new GuardrailMaterializedVersion(GUARDRAIL_IDENTIFIER, "DRAFT"));
        }

        @Test
        @DisplayName("AWS guardrailVersion 패턴을 벗어난 \"0\"은 거부된다")
        void rejectsZeroVersion() {
            assertThrows(IllegalArgumentException.class,
                    () -> new GuardrailMaterializedVersion(GUARDRAIL_IDENTIFIER, "0"));
        }

        @Test
        @DisplayName("빈 version은 거부된다")
        void rejectsBlankVersion() {
            assertThrows(IllegalArgumentException.class,
                    () -> new GuardrailMaterializedVersion(GUARDRAIL_IDENTIFIER, "   "));
        }

        @Test
        @DisplayName("빈 guardrail identifier는 거부된다")
        void rejectsBlankGuardrailIdentifier() {
            assertThrows(IllegalArgumentException.class,
                    () -> new GuardrailMaterializedVersion("   ", "7"));
        }
    }

    @Nested
    @DisplayName("Guardrail 실행 결과")
    class ExecutionResultTest {

        @Test
        @DisplayName("성공 결과는 action code만 노출한다")
        void exposesActionCodeOnSuccess() {
            GuardrailExecutionResult success = GuardrailExecutionResult.succeeded("NONE");

            assertEquals("NONE", success.actionCode());
            assertNull(success.failureCode());
        }

        @Test
        @DisplayName("실패 결과는 failure code만 노출한다")
        void exposesFailureCodeOnFailure() {
            GuardrailExecutionResult failure =
                    GuardrailExecutionResult.failed(GuardrailFailureCode.PROVIDER_UNAVAILABLE);

            assertEquals(GuardrailFailureCode.PROVIDER_UNAVAILABLE, failure.failureCode());
            assertNull(failure.actionCode());
        }

        @Test
        @DisplayName("action과 failure를 함께 설정하면 거부된다")
        void rejectsBothActionAndFailure() {
            assertThrows(IllegalArgumentException.class,
                    () -> new GuardrailExecutionResult("NONE", GuardrailFailureCode.PROVIDER_TIMEOUT));
        }

        @Test
        @DisplayName("action과 failure를 모두 비우면 거부된다")
        void rejectsMissingActionAndFailure() {
            assertThrows(IllegalArgumentException.class,
                    () -> new GuardrailExecutionResult(null, null));
        }

        @Test
        @DisplayName("빈 action code는 action으로 취급하지 않아 거부된다")
        void rejectsBlankActionCode() {
            assertThrows(IllegalArgumentException.class,
                    () -> new GuardrailExecutionResult("   ", null));
        }

        @Test
        @DisplayName("빈 action code와 failure code를 함께 설정하면 failure로 성립하고 isSuccess는 false다")
        void treatsBlankActionWithFailureAsFailure() {
            GuardrailExecutionResult result =
                    new GuardrailExecutionResult("   ", GuardrailFailureCode.PROVIDER_TIMEOUT);

            assertFalse(result.isSuccess());
            assertEquals(GuardrailFailureCode.PROVIDER_TIMEOUT, result.failureCode());
        }
    }
}
