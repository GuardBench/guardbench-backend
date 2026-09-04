package com.guardbench.evaluator.infrastructure.sagemaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;

import com.guardbench.testrun.application.port.out.EvaluatorExecutionPort;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.retries.api.RetryStrategy;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;

/** Worker 비활성화 시 SageMaker Evaluator SDK와 Port가 로딩되지 않는지 검증한다. */
class SageMakerEvaluatorConfigurationTest {

    @Test
    @DisplayName("worker가 비활성화되면 SageMakerRuntimeClient가 없다")
    void runtimeClientNotRegisteredWhenDisabled() {
        try (var context = createContext(Map.of("guardbench.worker.enabled", "false"))) {
            assertThat(context.getBeanNamesForType(SageMakerRuntimeClient.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("worker가 비활성화되면 EvaluatorExecutionPort가 없다")
    void evaluatorPortNotRegisteredWhenDisabled() {
        try (var context = createContext(Map.of("guardbench.worker.enabled", "false"))) {
            assertThat(context.getBeanNamesForType(EvaluatorExecutionPort.class)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Provider 호출 한도")
    class CallLimitTest {

        @Test
        @DisplayName("기본 설정은 전체 15초, 개별 시도 5초, 재시도 없음(1회)을 적용한다")
        void appliesApprovedCallLimits() {
            SageMakerProperties properties = new SageMakerProperties(null, null, 0L, 0L, 0);

            ClientOverrideConfiguration configuration =
                    SageMakerEvaluatorConfiguration.overrideConfiguration(properties);

            assertThat(configuration.apiCallTimeout()).contains(Duration.ofSeconds(15));
            assertThat(configuration.apiCallAttemptTimeout()).contains(Duration.ofSeconds(5));
            assertThat(configuration.retryStrategy())
                    .isPresent()
                    .get()
                    .extracting(RetryStrategy::maxAttempts)
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("전체 호출 한도가 claim lease를 넘으면 기동을 거부한다")
        void rejectsTimeoutBeyondClaimLease() {
            assertThatThrownBy(() -> new SageMakerProperties(null, null, 45_000L, 5_000L, 3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("45s execution claim lease");
        }

        @Test
        @DisplayName("개별 시도 한도가 전체 한도보다 크면 기동을 거부한다")
        void rejectsAttemptTimeoutGreaterThanTotal() {
            assertThatThrownBy(() -> new SageMakerProperties(null, null, 5_000L, 9_000L, 3))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("api-call-timeout-ms");
        }
    }

    private static AnnotationConfigApplicationContext createContext(Map<String, String> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.copyOf(properties))
        );
        context.register(SageMakerEvaluatorConfiguration.class);
        context.refresh();
        return context;
    }
}
