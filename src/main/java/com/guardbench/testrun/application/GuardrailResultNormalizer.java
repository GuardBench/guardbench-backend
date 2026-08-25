package com.guardbench.testrun.application;

import com.guardbench.testrun.application.port.out.GuardrailExecutionResult;
import com.guardbench.testrun.application.port.out.GuardrailFailureCode;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.ActualResult;
import com.guardbench.testrun.domain.TestExecutionError;
import com.guardbench.testrun.domain.TestExecutionErrorCode;

/**
 * AWS 응답을 TestRun이 소유한 결과로 변환하는 순수 Application normalizer다.
 *
 * <p>실제 Bedrock SDK 타입은 Guardrail Adapter에만 존재하고 이 클래스에는 들어오지 않는다.
 */
public final class GuardrailResultNormalizer {

    private static final String NONE_ACTION = "NONE";
    private static final String INTERVENED_ACTION = "GUARDRAIL_INTERVENED";

    private GuardrailResultNormalizer() {
    }

    public static GuardrailExecutionNormalization normalize(GuardrailExecutionResult providerResult) {
        if (providerResult == null) {
            return failed(GuardrailFailureCode.PROVIDER_RESPONSE_INVALID);
        }
        if (!providerResult.isSuccess()) {
            return failed(providerResult.failureCode());
        }

        return switch (providerResult.actionCode()) {
            case NONE_ACTION -> GuardrailExecutionNormalization.succeeded(
                    new ActualResult(Action.ALLOW)
            );
            case INTERVENED_ACTION -> GuardrailExecutionNormalization.succeeded(
                    new ActualResult(Action.BLOCK)
            );
            default -> failed(GuardrailFailureCode.PROVIDER_RESPONSE_INVALID);
        };
    }

    /**
     * failure code마다 Core 오류 코드와 공개 가능한 고정 메시지를 exhaustive switch로 고정한다.
     *
     * <p>Provider 오류는 항상 안전한 결과로 수렴해야 하므로 매핑 누락을 실행 시점 예외가 아니라
     * 컴파일 오류로 드러낸다. {@link GuardrailFailureCode}에 상수를 추가하면 이 switch가 컴파일에 실패한다.
     */
    private static GuardrailExecutionNormalization failed(GuardrailFailureCode failureCode) {
        TestExecutionError error = switch (failureCode) {
            case TARGET_NOT_FOUND -> new TestExecutionError(
                    TestExecutionErrorCode.TARGET_NOT_FOUND,
                    "Guardrail target was not found."
            );
            case TARGET_ACCESS_DENIED -> new TestExecutionError(
                    TestExecutionErrorCode.TARGET_ACCESS_DENIED,
                    "Guardrail target access was denied."
            );
            case TARGET_CONFIGURATION_INVALID -> new TestExecutionError(
                    TestExecutionErrorCode.TARGET_CONFIGURATION_INVALID,
                    "Guardrail target configuration is invalid."
            );
            case PROVIDER_UNAVAILABLE -> new TestExecutionError(
                    TestExecutionErrorCode.PROVIDER_UNAVAILABLE,
                    "Guardrail provider is unavailable."
            );
            case PROVIDER_RESPONSE_INVALID -> new TestExecutionError(
                    TestExecutionErrorCode.PROVIDER_RESPONSE_INVALID,
                    "Guardrail provider response is invalid."
            );
            case PROVIDER_TIMEOUT -> new TestExecutionError(
                    TestExecutionErrorCode.PROVIDER_TIMEOUT,
                    "Guardrail provider timed out."
            );
        };
        return GuardrailExecutionNormalization.failed(error);
    }
}
