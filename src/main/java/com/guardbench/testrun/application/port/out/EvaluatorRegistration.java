package com.guardbench.testrun.application.port.out;

/**
 * Provider SDK 타입을 노출하지 않는 실제 Evaluator(Response Behavior Classifier) 설정 값 계약이다.
 *
 * <p>catalog/profile 개념 없이 서비스 전역 고정 classifier 설정을 재현 가능하게 식별하는 데만 사용한다.
 *
 * @param providerCode classifier provider의 안정적인 코드 (예: {@code BEDROCK})
 * @param modelId      실제 실행에 사용한 provider-specific model 식별자
 */
public record EvaluatorRegistration(String providerCode, String modelId) {
    public EvaluatorRegistration {
        if (providerCode == null || providerCode.isBlank() || modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("evaluator registration must contain a provider and model id");
        }
    }
}
