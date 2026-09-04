package com.guardbench.evaluator.infrastructure.sagemaker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.guardbench.testrun.application.port.out.EvaluatorFailureCode;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sagemakerruntime.model.ModelErrorException;
import software.amazon.awssdk.services.sagemakerruntime.model.ModelNotReadyException;
import software.amazon.awssdk.services.sagemakerruntime.model.ValidationErrorException;

class SageMakerFailureCodeMapperTest {

    @Test
    @DisplayName("SageMaker AccessDeniedException은 EVALUATOR_ACCESS_DENIED로 변환한다")
    void mapsAccessDeniedToEvaluatorAccessDenied() {
        AwsServiceException exception = AwsServiceException.builder()
                .message("access denied")
                .awsErrorDetails(software.amazon.awssdk.awscore.exception.AwsErrorDetails.builder()
                        .errorCode("AccessDeniedException")
                        .build())
                .build();

        assertEquals(EvaluatorFailureCode.EVALUATOR_ACCESS_DENIED, SageMakerFailureCodeMapper.map(exception));
    }

    @Test
    @DisplayName("SageMaker ValidationError는 EVALUATOR_CONFIGURATION_INVALID로 변환한다")
    void mapsValidationErrorToEvaluatorConfigurationInvalid() {
        assertEquals(EvaluatorFailureCode.EVALUATOR_CONFIGURATION_INVALID,
                SageMakerFailureCodeMapper.map(ValidationErrorException.builder().build()));
    }

    @Test
    @DisplayName("ModelError, ModelNotReady, throttling과 SDK client 오류는 PROVIDER_UNAVAILABLE로 변환한다")
    void mapsTransientProviderFailuresToProviderUnavailable() {
        assertEquals(EvaluatorFailureCode.PROVIDER_UNAVAILABLE,
                SageMakerFailureCodeMapper.map(ModelErrorException.builder().build()));
        assertEquals(EvaluatorFailureCode.PROVIDER_UNAVAILABLE,
                SageMakerFailureCodeMapper.map(ModelNotReadyException.builder().build()));
        assertEquals(EvaluatorFailureCode.PROVIDER_UNAVAILABLE,
                SageMakerFailureCodeMapper.map(SdkClientException.create("hidden detail")));
    }
}
