package com.guardbench.evaluator.infrastructure.bedrock;

import com.guardbench.testrun.application.port.out.EvaluatorFailureCode;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkException;

final class BedrockGuardrailFailureCodeMapper {

    private BedrockGuardrailFailureCodeMapper() {
    }

    static EvaluatorFailureCode map(SdkException exception) {
        if (exception instanceof ApiCallTimeoutException
                || exception instanceof ApiCallAttemptTimeoutException) {
            return EvaluatorFailureCode.PROVIDER_TIMEOUT;
        }
        if (isResourceNotFound(exception)) {
            return EvaluatorFailureCode.EVALUATOR_NOT_FOUND;
        }
        if (isAccessDenied(exception)) {
            return EvaluatorFailureCode.EVALUATOR_ACCESS_DENIED;
        }
        if (isInvalidConfiguration(exception)) {
            return EvaluatorFailureCode.EVALUATOR_CONFIGURATION_INVALID;
        }
        if (exception instanceof AwsServiceException serviceException) {
            return mapServiceErrorCode(serviceException.awsErrorDetails());
        }
        return EvaluatorFailureCode.PROVIDER_UNAVAILABLE;
    }

    private static boolean isResourceNotFound(SdkException exception) {
        return exception instanceof software.amazon.awssdk.services.bedrock.model.ResourceNotFoundException
                || exception instanceof software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException;
    }

    private static boolean isAccessDenied(SdkException exception) {
        return exception instanceof software.amazon.awssdk.services.bedrock.model.AccessDeniedException
                || exception instanceof software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException;
    }

    private static boolean isInvalidConfiguration(SdkException exception) {
        return exception instanceof software.amazon.awssdk.services.bedrock.model.ValidationException
                || exception instanceof software.amazon.awssdk.services.bedrockruntime.model.ValidationException
                || exception instanceof software.amazon.awssdk.services.bedrock.model.ConflictException
                || exception instanceof software.amazon.awssdk.services.bedrockruntime.model.ConflictException;
    }

    private static EvaluatorFailureCode mapServiceErrorCode(AwsErrorDetails errorDetails) {
        if (errorDetails == null || errorDetails.errorCode() == null) {
            return EvaluatorFailureCode.PROVIDER_UNAVAILABLE;
        }
        return switch (errorDetails.errorCode()) {
            case "ResourceNotFoundException" -> EvaluatorFailureCode.EVALUATOR_NOT_FOUND;
            case "AccessDeniedException" -> EvaluatorFailureCode.EVALUATOR_ACCESS_DENIED;
            case "ValidationException", "ConflictException" -> EvaluatorFailureCode.EVALUATOR_CONFIGURATION_INVALID;
            default -> EvaluatorFailureCode.PROVIDER_UNAVAILABLE;
        };
    }
}
