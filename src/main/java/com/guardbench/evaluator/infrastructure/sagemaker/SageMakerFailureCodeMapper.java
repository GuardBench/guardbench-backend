package com.guardbench.evaluator.infrastructure.sagemaker;

import com.guardbench.testrun.application.port.out.EvaluatorFailureCode;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sagemakerruntime.model.ModelErrorException;
import software.amazon.awssdk.services.sagemakerruntime.model.ModelNotReadyException;
import software.amazon.awssdk.services.sagemakerruntime.model.ValidationErrorException;

/**
 * SageMaker Runtime {@code InvokeEndpoint} SDK 예외를 {@link EvaluatorFailureCode}로 안전하게
 * 수렴한다.
 *
 * <p>AWS가 문서화한 {@code InvokeEndpoint} 오류 전체는 {@code InternalDependencyException},
 * {@code InternalFailure}, {@code ModelError}, {@code ModelNotReadyException},
 * {@code ServiceUnavailable}, {@code ValidationError}다. {@code ThrottlingException}은
 * Common Error Type으로 별도 발생할 수 있다.
 *
 * <ul>
 *   <li>{@code ThrottlingException}, {@code ModelError}, {@code InternalFailure},
 *       {@code InternalDependencyException}, {@code ServiceUnavailable},
 *       {@code ModelNotReadyException} — 재시도 가능한 일시 실패({@code PROVIDER_UNAVAILABLE})
 *   <li>{@code ValidationError} — endpoint 미존재/요청 형식 오류를 포함하는 구성 오류
 *       ({@code EVALUATOR_CONFIGURATION_INVALID})
 *   <li>{@code AccessDeniedException} — 권한 거부({@code EVALUATOR_ACCESS_DENIED})
 * </ul>
 */
final class SageMakerFailureCodeMapper {

    private SageMakerFailureCodeMapper() {
    }

    static EvaluatorFailureCode map(SdkException exception) {
        if (exception instanceof ApiCallTimeoutException
                || exception instanceof ApiCallAttemptTimeoutException) {
            return EvaluatorFailureCode.PROVIDER_TIMEOUT;
        }
        if (exception instanceof ValidationErrorException) {
            return EvaluatorFailureCode.EVALUATOR_CONFIGURATION_INVALID;
        }
        if (exception instanceof ModelErrorException || exception instanceof ModelNotReadyException) {
            return EvaluatorFailureCode.PROVIDER_UNAVAILABLE;
        }
        if (exception instanceof AwsServiceException serviceException) {
            return mapServiceErrorCode(serviceException.awsErrorDetails());
        }
        return EvaluatorFailureCode.PROVIDER_UNAVAILABLE;
    }

    private static EvaluatorFailureCode mapServiceErrorCode(AwsErrorDetails errorDetails) {
        if (errorDetails == null || errorDetails.errorCode() == null) {
            return EvaluatorFailureCode.PROVIDER_UNAVAILABLE;
        }
        return switch (errorDetails.errorCode()) {
            case "AccessDeniedException" -> EvaluatorFailureCode.EVALUATOR_ACCESS_DENIED;
            case "ValidationError", "ValidationException" -> EvaluatorFailureCode.EVALUATOR_CONFIGURATION_INVALID;
            case "ThrottlingException", "InternalFailure", "InternalDependencyException", "ServiceUnavailable" ->
                    EvaluatorFailureCode.PROVIDER_UNAVAILABLE;
            default -> EvaluatorFailureCode.PROVIDER_UNAVAILABLE;
        };
    }
}
