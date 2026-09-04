package com.guardbench.evaluator.infrastructure.sagemaker;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SageMaker Response Behavior Classifier 호출 설정이다.
 *
 * <p>최종 endpoint name과 classifier system prompt 최종 버전은 별도 실험으로 확정 중이며(#173), 이
 * 설정은 실험 결과값을 배포 시점에 주입할 수 있는 placeholder다. {@code endpointName}이 비어 있으면
 * 애플리케이션은 정상 기동하지만 classifier를 실제로 호출하는 시점(TestRun 접수의 evaluator reference
 * 등록, Worker의 evaluate 호출)에 실패한다. 값이 확정되기 전에 운영 환경에서 classifier를 활성화하지
 * 않는다.
 */
@ConfigurationProperties(prefix = "guardbench.sagemaker.classifier")
record SageMakerClassifierProperties(
        String endpointName,
        String systemPrompt,
        String userPromptTemplate
) {

    static final String UNCONFIGURED_ENDPOINT_NAME = "UNCONFIGURED";
    static final String DEFAULT_USER_PROMPT_TEMPLATE = """
            USER REQUEST:
            %s

            ASSISTANT RESPONSE:
            %s""";

    SageMakerClassifierProperties {
        if (endpointName == null || endpointName.isBlank()) {
            endpointName = UNCONFIGURED_ENDPOINT_NAME;
        }
        if (systemPrompt == null || systemPrompt.isBlank()) {
            systemPrompt = "";
        }
        if (userPromptTemplate == null || userPromptTemplate.isBlank()) {
            userPromptTemplate = DEFAULT_USER_PROMPT_TEMPLATE;
        }
    }

    boolean isConfigured() {
        return !UNCONFIGURED_ENDPOINT_NAME.equals(endpointName) && !systemPrompt.isBlank();
    }

    String renderUserMessage(String prompt, String applicationResponse) {
        return userPromptTemplate.formatted(prompt, applicationResponse);
    }
}
