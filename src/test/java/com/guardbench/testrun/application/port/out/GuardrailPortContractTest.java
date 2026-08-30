package com.guardbench.testrun.application.port.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class GuardrailPortContractTest {

    private static final com.guardbench.testrun.domain.TargetReference TARGET_REFERENCE =
            new com.guardbench.testrun.domain.TargetReference("target-ref-1");

    @Nested
    @DisplayName("Target 준비 요청")
    class MaterializationRequestTest {

        @Test
        @DisplayName("준비 요청은 TestRun별 결정적 idempotency token을 생성한다")
        void createsDeterministicClientRequestToken() {
            TargetPreparationRequest request = new TargetPreparationRequest(TARGET_REFERENCE, 42);

            assertEquals("guardbench-test-run-42", request.idempotencyToken());
        }

        @Test
        @DisplayName("Target reference null은 거부된다")
        void rejectsNullTargetReference() {
            assertThrows(NullPointerException.class,
                    () -> new TargetPreparationRequest(null, 42));
        }

        @Test
        @DisplayName("양수가 아닌 testRunId는 거부된다")
        void rejectsNonPositiveTestRunId() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TargetPreparationRequest(TARGET_REFERENCE, 0));
        }
    }

    @Nested
    @DisplayName("Guardrail 실행 결과")
    class ExecutionResultTest {

        @Test
        @DisplayName("성공 결과는 action code만 노출한다")
        void exposesActionCodeOnSuccess() {
            TargetExecutionResult success = TargetExecutionResult.succeeded("NONE");

            assertEquals("NONE", success.actionCode());
            assertNull(success.failureCode());
        }

        @Test
        @DisplayName("실패 결과는 failure code만 노출한다")
        void exposesFailureCodeOnFailure() {
            TargetExecutionResult failure =
                    TargetExecutionResult.failed(TargetFailureCode.PROVIDER_UNAVAILABLE);

            assertEquals(TargetFailureCode.PROVIDER_UNAVAILABLE, failure.failureCode());
            assertNull(failure.actionCode());
        }

        @Test
        @DisplayName("action과 failure를 함께 설정하면 거부된다")
        void rejectsBothActionAndFailure() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TargetExecutionResult("NONE", TargetFailureCode.PROVIDER_TIMEOUT));
        }

        @Test
        @DisplayName("action과 failure를 모두 비우면 거부된다")
        void rejectsMissingActionAndFailure() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TargetExecutionResult(null, null));
        }

        @Test
        @DisplayName("빈 action code는 action으로 취급하지 않아 거부된다")
        void rejectsBlankActionCode() {
            assertThrows(IllegalArgumentException.class,
                    () -> new TargetExecutionResult("   ", null));
        }

        @Test
        @DisplayName("빈 action code와 failure code를 함께 설정하면 failure로 성립하고 isSuccess는 false다")
        void treatsBlankActionWithFailureAsFailure() {
            TargetExecutionResult result =
                    new TargetExecutionResult("   ", TargetFailureCode.PROVIDER_TIMEOUT);

            assertFalse(result.isSuccess());
            assertEquals(TargetFailureCode.PROVIDER_TIMEOUT, result.failureCode());
        }
    }
}
