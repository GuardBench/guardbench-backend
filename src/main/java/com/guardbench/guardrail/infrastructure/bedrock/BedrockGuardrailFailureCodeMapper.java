package com.guardbench.guardrail.infrastructure.bedrock;

import com.guardbench.testrun.application.port.out.GuardrailFailureCode;

import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkException;

final class BedrockGuardrailFailureCodeMapper {

    private BedrockGuardrailFailureCodeMapper() {
    }

    static GuardrailFailureCode map(SdkException exception) {
        if (exception instanceof ApiCallTimeoutException
                || exception instanceof ApiCallAttemptTimeoutException) {
            return GuardrailFailureCode.PROVIDER_TIMEOUT;
        }
        if (isResourceNotFound(exception)) {
            return GuardrailFailureCode.TARGET_NOT_FOUND;
        }
        if (isAccessDenied(exception)) {
            return GuardrailFailureCode.TARGET_ACCESS_DENIED;
        }
        if (isInvalidConfiguration(exception)) {
            return GuardrailFailureCode.TARGET_CONFIGURATION_INVALID;
        }
        if (exception instanceof AwsServiceException serviceException) {
            return mapServiceErrorCode(serviceException.awsErrorDetails());
        }
        return GuardrailFailureCode.PROVIDER_UNAVAILABLE;
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

    private static GuardrailFailureCode mapServiceErrorCode(AwsErrorDetails errorDetails) {
        if (errorDetails == null || errorDetails.errorCode() == null) {
            return GuardrailFailureCode.PROVIDER_UNAVAILABLE;
        }
        return switch (errorDetails.errorCode()) {
            case "ResourceNotFoundException" -> GuardrailFailureCode.TARGET_NOT_FOUND;
            case "AccessDeniedException" -> GuardrailFailureCode.TARGET_ACCESS_DENIED;
            case "ValidationException", "ConflictException" -> GuardrailFailureCode.TARGET_CONFIGURATION_INVALID;
            default -> GuardrailFailureCode.PROVIDER_UNAVAILABLE;
        };
    }
}
