package com.guardbench.testrun.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.guardbench.testrun.application.ExecuteTestRunService;
import com.guardbench.testrun.application.ResolveTestRunService;

/**
 * Worker 비활성화 시 WorkerServiceConfiguration의 서비스 빈이 로딩되지 않음을 검증한다.
 *
 * <p>DB, JPA, 웹 없이 가벼운 AnnotationConfigApplicationContext로 검증한다.
 */
class WorkerServiceConfigurationTest {

    @Test
    @DisplayName("guardbench.worker.enabled=false이면 ResolveTestRunService 빈이 없다")
    void resolveServiceNotRegisteredWhenDisabled() {
        try (var context = createContext(Map.of(
                "guardbench.worker.enabled", "false"
        ))) {
            assertThat(context.getBeanNamesForType(ResolveTestRunService.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("guardbench.worker.enabled=false이면 ExecuteTestRunService 빈이 없다")
    void executeServiceNotRegisteredWhenDisabled() {
        try (var context = createContext(Map.of(
                "guardbench.worker.enabled", "false"
        ))) {
            assertThat(context.getBeanNamesForType(ExecuteTestRunService.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("guardbench.worker.enabled 미설정이면 Worker 서비스 빈이 없다")
    void workerServiceNotRegisteredWhenPropertyMissing() {
        try (var context = createContext(Map.of())) {
            assertThat(context.getBeanNamesForType(ResolveTestRunService.class)).isEmpty();
            assertThat(context.getBeanNamesForType(ExecuteTestRunService.class)).isEmpty();
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────

    private static AnnotationConfigApplicationContext createContext(Map<String, String> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.copyOf(properties))
        );
        context.register(WorkerServiceConfiguration.class);
        context.refresh();
        return context;
    }
}
