package com.guardbench.testrun.application;

import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;
import com.guardbench.testrun.domain.ApplicationResponse;
import com.guardbench.testrun.domain.TestExecutionError;
import com.guardbench.testrun.domain.TestExecutionErrorCode;
import com.guardbench.testrun.domain.TestExecutionErrorStage;

/**
 * AWS 응답을 TestRun이 소유한 결과로 변환하는 순수 Application normalizer다.
 *
 * <p>실제 Bedrock SDK 타입은 Guardrail Adapter에만 존재하고 이 클래스에는 들어오지 않는다.
 */
public final class TargetResultNormalizer {

    private TargetResultNormalizer() {
    }

    public static TargetExecutionNormalization normalize(TargetExecutionResult providerResult) {
        if (providerResult == null) {
            return failed(TargetFailureCode.PROVIDER_RESPONSE_INVALID);
        }
        if (!providerResult.isSuccess()) {
            return failed(providerResult.failureCode());
        }

        return TargetExecutionNormalization.succeeded(new ApplicationResponse(providerResult.response()));
    }

    /**
     * failure code마다 Core 오류 코드와 공개 가능한 고정 메시지를 exhaustive switch로 고정한다.
     *
     * <p>Provider 오류는 항상 안전한 결과로 수렴해야 하므로 매핑 누락을 실행 시점 예외가 아니라
     * 컴파일 오류로 드러낸다. {@link GuardrailFailureCode}에 상수를 추가하면 이 switch가 컴파일에 실패한다.
     */
    private static TargetExecutionNormalization failed(TargetFailureCode failureCode) {
        TestExecutionError error = switch (failureCode) {
            case TARGET_NOT_FOUND -> new TestExecutionError(
                    TestExecutionErrorStage.APPLICATION_TARGET,
                    TestExecutionErrorCode.TARGET_NOT_FOUND,
                    "Application target was not found."
            );
            case TARGET_ACCESS_DENIED -> new TestExecutionError(
                    TestExecutionErrorStage.APPLICATION_TARGET,
                    TestExecutionErrorCode.TARGET_ACCESS_DENIED,
                    "Application target access was denied."
            );
            case TARGET_CONFIGURATION_INVALID -> new TestExecutionError(
                    TestExecutionErrorStage.APPLICATION_TARGET,
                    TestExecutionErrorCode.TARGET_CONFIGURATION_INVALID,
                    "Application target configuration is invalid."
            );
            case PROVIDER_UNAVAILABLE -> new TestExecutionError(
                    TestExecutionErrorStage.APPLICATION_TARGET,
                    TestExecutionErrorCode.PROVIDER_UNAVAILABLE,
                    "Application target provider is unavailable."
            );
            case PROVIDER_RESPONSE_INVALID -> new TestExecutionError(
                    TestExecutionErrorStage.APPLICATION_TARGET,
                    TestExecutionErrorCode.PROVIDER_RESPONSE_INVALID,
                    "Application target response is invalid."
            );
            case PROVIDER_TIMEOUT -> new TestExecutionError(
                    TestExecutionErrorStage.APPLICATION_TARGET,
                    TestExecutionErrorCode.PROVIDER_TIMEOUT,
                    "Application target provider timed out."
            );
        };
        return TargetExecutionNormalization.failed(error);
    }
}
