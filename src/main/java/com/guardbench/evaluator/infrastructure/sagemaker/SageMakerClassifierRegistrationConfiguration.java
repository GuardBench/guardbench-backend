package com.guardbench.evaluator.infrastructure.sagemaker;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guardbench.testrun.application.port.out.EvaluatorRegistration;

/**
 * TestRun 접수(API 모드)와 Worker 실행(Worker 모드) 양쪽 모두에서 필요한 Response Behavior
 * Classifier의 provider/endpoint 식별자를 조립한다.
 *
 * <p>{@link SageMakerEvaluatorConfiguration}과 달리 {@code guardbench.worker.enabled} 조건 없이
 * 항상 활성화된다. TestRun 접수 시 실제 실행에 사용할 classifier를 재현 가능하게
 * {@link com.guardbench.testrun.domain.EvaluatorReference}로 고정하려면 API 모드에서도 이 식별자가
 * 필요하다.
 */
@Configuration
@EnableConfigurationProperties(SageMakerClassifierProperties.class)
class SageMakerClassifierRegistrationConfiguration {

    private static final String PROVIDER_CODE = "SAGEMAKER";

    @Bean
    EvaluatorRegistration evaluatorRegistration(SageMakerClassifierProperties classifierProperties) {
        return new EvaluatorRegistration(PROVIDER_CODE, classifierProperties.endpointName());
    }
}
