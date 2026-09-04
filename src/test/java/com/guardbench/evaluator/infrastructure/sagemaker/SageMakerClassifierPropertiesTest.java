package com.guardbench.evaluator.infrastructure.sagemaker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code userPromptTemplate}이 (prompt, actualResponse) classifier 계약을 위반하는 잘못된 형식이면
 * {@code isConfigured()}가 안전하게 미설정으로 판정하는지 검증한다.
 */
class SageMakerClassifierPropertiesTest {

    private static final String ENDPOINT_NAME = "test-endpoint";
    private static final String SYSTEM_PROMPT = "system prompt";

    @Test
    @DisplayName("기본 템플릿(비어있음)은 prompt와 actualResponse를 모두 렌더링하므로 설정된 것으로 판정한다")
    void defaultTemplateIsConfigured() {
        SageMakerClassifierProperties properties =
                new SageMakerClassifierProperties(ENDPOINT_NAME, SYSTEM_PROMPT, null);

        assertTrue(properties.isConfigured());
        String rendered = properties.renderUserMessage("prompt-value", "response-value");
        assertTrue(rendered.contains("prompt-value"));
        assertTrue(rendered.contains("response-value"));
    }

    @Test
    @DisplayName("두 placeholder를 모두 포함한 커스텀 템플릿은 설정된 것으로 판정한다")
    void customTemplateWithBothPlaceholdersIsConfigured() {
        SageMakerClassifierProperties properties = new SageMakerClassifierProperties(
                ENDPOINT_NAME, SYSTEM_PROMPT, "REQUEST=%s RESPONSE=%s");

        assertTrue(properties.isConfigured());
        String rendered = properties.renderUserMessage("prompt-value", "response-value");
        assertTrue(rendered.contains("prompt-value"));
        assertTrue(rendered.contains("response-value"));
    }

    @Test
    @DisplayName("%s가 하나뿐인 템플릿은 actualResponse가 누락되므로 설정되지 않은 것으로 판정한다")
    void templateWithSinglePlaceholderIsNotConfigured() {
        SageMakerClassifierProperties properties = new SageMakerClassifierProperties(
                ENDPOINT_NAME, SYSTEM_PROMPT, "REQUEST=%s");

        assertFalse(properties.isConfigured());
    }

    @Test
    @DisplayName("같은 인덱스 placeholder만 반복하는 템플릿은 prompt만 두 번 렌더링되므로 설정되지 않은 것으로 판정한다")
    void templateWithRepeatedSameIndexPlaceholderIsNotConfigured() {
        SageMakerClassifierProperties properties = new SageMakerClassifierProperties(
                ENDPOINT_NAME, SYSTEM_PROMPT, "REQUEST=%1$s RESPONSE=%1$s");

        assertFalse(properties.isConfigured());
    }

    @Test
    @DisplayName("placeholder가 전혀 없는 템플릿은 설정되지 않은 것으로 판정한다")
    void templateWithoutPlaceholdersIsNotConfigured() {
        SageMakerClassifierProperties properties = new SageMakerClassifierProperties(
                ENDPOINT_NAME, SYSTEM_PROMPT, "static text without placeholders");

        assertFalse(properties.isConfigured());
    }

    @Test
    @DisplayName("endpoint-name이 비어 있으면 설정되지 않은 것으로 판정한다")
    void blankEndpointNameIsNotConfigured() {
        SageMakerClassifierProperties properties =
                new SageMakerClassifierProperties(null, SYSTEM_PROMPT, null);

        assertFalse(properties.isConfigured());
    }

    @Test
    @DisplayName("system-prompt가 비어 있으면 설정되지 않은 것으로 판정한다")
    void blankSystemPromptIsNotConfigured() {
        SageMakerClassifierProperties properties =
                new SageMakerClassifierProperties(ENDPOINT_NAME, null, null);

        assertFalse(properties.isConfigured());
    }
}
