package com.guardbench.guardrail.infrastructure.bedrock;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.guardbench.testrun.application.port.out.GuardrailExecutionPort;
import com.guardbench.testrun.application.port.out.GuardrailMaterializationPort;

import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Worker 비활성화 시 Bedrock 클라이언트와 Port 어댑터 빈이 로딩되지 않음을 검증한다.
 */
class BedrockWorkerConfigurationTest {

    @Test
    @DisplayName("guardbench.worker.enabled=false이면 BedrockClient 빈이 없다")
    void bedrockClientNotRegisteredWhenDisabled() {
        try (var context = createContext(Map.of(
                "guardbench.worker.enabled", "false"
        ))) {
            assertThat(context.getBeanNamesForType(BedrockClient.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("guardbench.worker.enabled=false이면 BedrockRuntimeClient 빈이 없다")
    void bedrockRuntimeClientNotRegisteredWhenDisabled() {
        try (var context = createContext(Map.of(
                "guardbench.worker.enabled", "false"
        ))) {
            assertThat(context.getBeanNamesForType(BedrockRuntimeClient.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("guardbench.worker.enabled=false이면 GuardrailMaterializationPort 빈이 없다")
    void materializationPortNotRegisteredWhenDisabled() {
        try (var context = createContext(Map.of(
                "guardbench.worker.enabled", "false"
        ))) {
            assertThat(context.getBeanNamesForType(GuardrailMaterializationPort.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("guardbench.worker.enabled=false이면 GuardrailExecutionPort 빈이 없다")
    void executionPortNotRegisteredWhenDisabled() {
        try (var context = createContext(Map.of(
                "guardbench.worker.enabled", "false"
        ))) {
            assertThat(context.getBeanNamesForType(GuardrailExecutionPort.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("guardbench.worker.enabled 미설정이면 Bedrock 빈이 없다")
    void bedrockBeansNotRegisteredWhenPropertyMissing() {
        try (var context = createContext(Map.of())) {
            assertThat(context.getBeanNamesForType(BedrockClient.class)).isEmpty();
            assertThat(context.getBeanNamesForType(BedrockRuntimeClient.class)).isEmpty();
            assertThat(context.getBeanNamesForType(GuardrailMaterializationPort.class)).isEmpty();
            assertThat(context.getBeanNamesForType(GuardrailExecutionPort.class)).isEmpty();
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────

    private static AnnotationConfigApplicationContext createContext(Map<String, String> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.copyOf(properties))
        );
        context.register(BedrockWorkerConfiguration.class);
        context.refresh();
        return context;
    }
}
