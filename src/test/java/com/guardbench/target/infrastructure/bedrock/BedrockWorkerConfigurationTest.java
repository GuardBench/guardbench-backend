package com.guardbench.target.infrastructure.bedrock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.guardbench.testrun.application.port.out.TargetExecutionPort;
import com.guardbench.testrun.application.port.out.TargetPreparationPort;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.retries.api.RetryStrategy;
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
            assertThat(context.getBeanNamesForType(TargetPreparationPort.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("guardbench.worker.enabled=false이면 TargetExecutionPort 빈이 없다")
    void executionPortNotRegisteredWhenDisabled() {
        try (var context = createContext(Map.of(
                "guardbench.worker.enabled", "false"
        ))) {
            assertThat(context.getBeanNamesForType(TargetExecutionPort.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("guardbench.worker.enabled 미설정이면 Bedrock 빈이 없다")
    void bedrockBeansNotRegisteredWhenPropertyMissing() {
        try (var context = createContext(Map.of())) {
            assertThat(context.getBeanNamesForType(BedrockClient.class)).isEmpty();
            assertThat(context.getBeanNamesForType(BedrockRuntimeClient.class)).isEmpty();
            assertThat(context.getBeanNamesForType(TargetPreparationPort.class)).isEmpty();
            assertThat(context.getBeanNamesForType(TargetExecutionPort.class)).isEmpty();
        }
    }

    @Nested
    @DisplayName("ADR 0005 Provider 호출 한도")
    class CallLimitTest {

        @Test
        @DisplayName("기본 설정은 전체 호출 15초, 개별 시도 5초, 최대 3회 시도를 적용한다")
        void appliesApprovedCallLimits() {
            BedrockProperties properties = new BedrockProperties(null, null, 0L, 0L, 0);

            ClientOverrideConfiguration configuration =
                    BedrockWorkerConfiguration.overrideConfiguration(properties);

            assertThat(configuration.apiCallTimeout()).contains(Duration.ofSeconds(15));
            assertThat(configuration.apiCallAttemptTimeout()).contains(Duration.ofSeconds(5));
            assertThat(configuration.retryStrategy())
                    .isPresent()
                    .get()
                    .extracting(RetryStrategy::maxAttempts)
                    .isEqualTo(3);
        }

        @Test
        @DisplayName("설정한 한도와 최대 시도 횟수를 그대로 적용한다")
        void appliesConfiguredCallLimits() {
            BedrockProperties properties = new BedrockProperties("ap-northeast-2", null, 9_000L, 3_000L, 2);

            ClientOverrideConfiguration configuration =
                    BedrockWorkerConfiguration.overrideConfiguration(properties);

            assertThat(configuration.apiCallTimeout()).contains(Duration.ofSeconds(9));
            assertThat(configuration.apiCallAttemptTimeout()).contains(Duration.ofSeconds(3));
            assertThat(configuration.retryStrategy())
                    .isPresent()
                    .get()
                    .extracting(RetryStrategy::maxAttempts)
                    .isEqualTo(2);
        }

        @Test
        @DisplayName("전체 호출 한도가 claim lease(45초)를 넘으면 기동을 거부한다")
        void rejectsTimeoutBeyondClaimLease() {
            assertThatThrownBy(() -> new BedrockProperties(null, null, 45_000L, 5_000L, 3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("45s execution claim lease");
        }

        @Test
        @DisplayName("개별 시도 한도가 전체 한도보다 크면 기동을 거부한다")
        void rejectsAttemptTimeoutGreaterThanTotal() {
            assertThatThrownBy(() -> new BedrockProperties(null, null, 5_000L, 9_000L, 3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("api-call-timeout-ms");
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
