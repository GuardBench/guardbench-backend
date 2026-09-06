package com.guardbench.testdefinition.infrastructure.cleanup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import com.guardbench.testdefinition.application.TestCaseBulkIdempotencyCleanupService;

class TestCaseBulkIdempotencyCleanupSchedulerTest {

    @Test
    @DisplayName("cleanup을 비활성화하면 scheduler와 scheduling infrastructure를 등록하지 않는다")
    void disabledCleanupDoesNotRegisterScheduling() {
        try (var context = createContext(Map.of(
                "guardbench.test-case-bulk-idempotency.cleanup.enabled", "false"))) {
            assertThat(context.getBeanNamesForType(TestCaseBulkIdempotencyCleanupScheduler.class)).isEmpty();
            assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("cleanup은 미설정 시 운영 기본값으로 활성화된다")
    void cleanupIsEnabledByDefault() {
        try (var context = createContext(Map.of(
                "guardbench.test-case-bulk-idempotency.cleanup.initial-delay-ms", "3600000"))) {
            assertThat(context.getBeanNamesForType(TestCaseBulkIdempotencyCleanupScheduler.class)).hasSize(1);
            assertThat(context.getBean(TestCaseBulkIdempotencyCleanupProperties.class).batchSize()).isEqualTo(500);
        }
    }

    @Test
    @DisplayName("가득 찬 batch만 이어서 삭제하고 빈 batch에서 실행을 마친다")
    void deletesBoundedBatchesUntilDrained() {
        TestCaseBulkIdempotencyCleanupService service = mock(TestCaseBulkIdempotencyCleanupService.class);
        when(service.deleteExpiredBatch(5)).thenReturn(5, 5, 2);
        TestCaseBulkIdempotencyCleanupScheduler scheduler = new TestCaseBulkIdempotencyCleanupScheduler(
                service, properties(5, 10));

        scheduler.deleteExpiredRecords();

        verify(service, times(3)).deleteExpiredBatch(5);
    }

    @Test
    @DisplayName("실행당 최대 batch 수를 넘겨 DB 부하를 증폭하지 않는다")
    void stopsAtMaximumBatchesPerRun() {
        TestCaseBulkIdempotencyCleanupService service = mock(TestCaseBulkIdempotencyCleanupService.class);
        when(service.deleteExpiredBatch(5)).thenReturn(5);
        TestCaseBulkIdempotencyCleanupScheduler scheduler = new TestCaseBulkIdempotencyCleanupScheduler(
                service, properties(5, 2));

        scheduler.deleteExpiredRecords();

        verify(service, times(2)).deleteExpiredBatch(5);
    }

    @Test
    @DisplayName("한 주기의 삭제 실패가 다음 스케줄 실행을 중단시키지 않는다")
    void failureDoesNotCrashScheduler() {
        TestCaseBulkIdempotencyCleanupService service = mock(TestCaseBulkIdempotencyCleanupService.class);
        when(service.deleteExpiredBatch(5))
                .thenThrow(new RuntimeException("simulated cleanup failure"))
                .thenReturn(0);
        TestCaseBulkIdempotencyCleanupScheduler scheduler = new TestCaseBulkIdempotencyCleanupScheduler(
                service, properties(5, 2));

        assertThatCode(() -> {
            scheduler.deleteExpiredRecords();
            scheduler.deleteExpiredRecords();
        }).doesNotThrowAnyException();

        verify(service, times(2)).deleteExpiredBatch(5);
    }

    private static TestCaseBulkIdempotencyCleanupProperties properties(int batchSize, int maxBatchesPerRun) {
        return new TestCaseBulkIdempotencyCleanupProperties(batchSize, maxBatchesPerRun, 900_000L, 60_000L);
    }

    private static AnnotationConfigApplicationContext createContext(Map<String, String> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.copyOf(properties)));
        context.register(
                TestCaseBulkIdempotencyCleanupConfiguration.class,
                TestCaseBulkIdempotencyCleanupScheduler.class,
                StubCleanupServiceConfiguration.class);
        context.refresh();
        return context;
    }

    @Configuration(proxyBeanMethods = false)
    static class StubCleanupServiceConfiguration {

        @Bean
        TestCaseBulkIdempotencyCleanupService cleanupService() {
            return mock(TestCaseBulkIdempotencyCleanupService.class);
        }
    }
}
