package com.guardbench.testrun.application;

import java.util.Map;

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

    private static final Map<GuardrailFailureCode, String> SAFE_MESSAGES = Map.of(
            GuardrailFailureCode.TARGET_NOT_FOUND, "Guardrail target was not found.",
            GuardrailFailureCode.TARGET_ACCESS_DENIED, "Guardrail target access was denied.",
            GuardrailFailureCode.TARGET_CONFIGURATION_INVALID, "Guardrail target configuration is invalid.",
            GuardrailFailureCode.PROVIDER_UNAVAILABLE, "Guardrail provider is unavailable.",
            GuardrailFailureCode.PROVIDER_RESPONSE_INVALID, "Guardrail provider response is invalid.",
            GuardrailFailureCode.PROVIDER_TIMEOUT, "Guardrail provider timed out."
    );

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

    private static GuardrailExecutionNormalization failed(GuardrailFailureCode failureCode) {
        return GuardrailExecutionNormalization.failed(new TestExecutionError(
                TestExecutionErrorCode.valueOf(failureCode.name()),
                SAFE_MESSAGES.get(failureCode)
        ));
    }
}
