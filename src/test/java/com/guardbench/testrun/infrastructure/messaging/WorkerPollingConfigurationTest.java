package com.guardbench.testrun.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;

import com.guardbench.testrun.application.messaging.TestRunQueue;

/**
 * Worker 런타임 와이어링 검증 테스트다.
 *
 * <p>guardbench.worker.enabled 속성에 따라 스케줄링/폴링 빈이
 * 조건부로 등록되는지 검증한다.
 *
 * <p>DB, JPA, 웹 없이 가벼운 AnnotationConfigApplicationContext로 검증한다.
 */
class WorkerPollingConfigurationTest {

    @Nested
    @DisplayName("Worker 비활성화 시 폴링 스케줄러 미등록")
    class WorkerDisabledTest {

        @Test
        @DisplayName("guardbench.worker.enabled=false이면 SqsPollingScheduler 빈이 없다")
        void schedulerBeanNotRegistered() {
            try (var context = createContext(Map.of(
                    "guardbench.worker.enabled", "false",
                    "guardbench.sqs.enabled", "false"
            ), SqsPollingScheduler.class, SqsWorkerConfiguration.class)) {
                assertThat(context.getBeanNamesForType(SqsPollingScheduler.class)).isEmpty();
            }
        }

        @Test
        @DisplayName("guardbench.worker.enabled 미설정이면 SqsPollingScheduler 빈이 없다")
        void schedulerBeanNotRegisteredWhenPropertyMissing() {
            try (var context = createContext(Map.of(),
                    SqsPollingScheduler.class, SqsWorkerConfiguration.class)) {
                assertThat(context.getBeanNamesForType(SqsPollingScheduler.class)).isEmpty();
            }
        }

        @Test
        @DisplayName("Worker 비활성화 시 EnableScheduling이 활성화되지 않는다")
        void schedulingNotActive() {
            try (var context = createContext(Map.of(
                    "guardbench.worker.enabled", "false",
                    "guardbench.sqs.enabled", "false"
            ), SqsPollingScheduler.class, SqsWorkerConfiguration.class)) {
                assertThat(context.getBeanNamesForType(ScheduledAnnotationBeanPostProcessor.class)).isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("Worker 활성화 시 폴링 스케줄러 등록 및 위임")
    class WorkerEnabledTest {

        @Test
        @DisplayName("guardbench.worker.enabled=true이면 SqsPollingScheduler가 조건부 활성화된다")
        void schedulerBeanRegistered() {
            try (var context = createContext(Map.of(
                    "guardbench.worker.enabled", "true",
                    "guardbench.sqs.enabled", "true",
                    "guardbench.sqs.region", "ap-northeast-2"
            ), SqsPollingScheduler.class, StubAdaptersConfig.class)) {
                assertThat(context.getBeanNamesForType(SqsPollingScheduler.class)).isNotEmpty();
            }
        }

        @Test
        @DisplayName("pollResolveQueue가 RESOLVE adapter의 poll()에 위임한다")
        void pollResolveDelegates() {
            SqsInboundPollingAdapter resolveAdapter = mock(SqsInboundPollingAdapter.class);

            Map<TestRunQueue, SqsInboundPollingAdapter> adapters = new EnumMap<>(TestRunQueue.class);
            adapters.put(TestRunQueue.RESOLVE, resolveAdapter);

            SqsPollingScheduler scheduler = new SqsPollingScheduler(adapters);
            scheduler.pollResolveQueue();
            scheduler.pollResolveQueue();

            verify(resolveAdapter, times(2)).poll();
        }

        @Test
        @DisplayName("pollWorkItemsQueue가 WORK_ITEMS adapter의 poll()에 위임한다")
        void pollWorkItemsDelegates() {
            SqsInboundPollingAdapter workItemsAdapter = mock(SqsInboundPollingAdapter.class);

            Map<TestRunQueue, SqsInboundPollingAdapter> adapters = new EnumMap<>(TestRunQueue.class);
            adapters.put(TestRunQueue.WORK_ITEMS, workItemsAdapter);

            SqsPollingScheduler scheduler = new SqsPollingScheduler(adapters);
            scheduler.pollWorkItemsQueue();

            verify(workItemsAdapter, times(1)).poll();
        }

        @Test
        @DisplayName("pollFinalizeQueue가 FINALIZE adapter의 poll()에 위임한다")
        void pollFinalizeDelegates() {
            SqsInboundPollingAdapter finalizeAdapter = mock(SqsInboundPollingAdapter.class);

            Map<TestRunQueue, SqsInboundPollingAdapter> adapters = new EnumMap<>(TestRunQueue.class);
            adapters.put(TestRunQueue.FINALIZE, finalizeAdapter);

            SqsPollingScheduler scheduler = new SqsPollingScheduler(adapters);
            scheduler.pollFinalizeQueue();
            scheduler.pollFinalizeQueue();
            scheduler.pollFinalizeQueue();

            verify(finalizeAdapter, times(3)).poll();
        }

        @Test
        @DisplayName("adapter가 없는 queue는 안전하게 no-op 처리한다")
        void missingAdapterIsNoOp() {
            SqsInboundPollingAdapter resolveAdapter = mock(SqsInboundPollingAdapter.class);

            Map<TestRunQueue, SqsInboundPollingAdapter> adapters = new EnumMap<>(TestRunQueue.class);
            adapters.put(TestRunQueue.RESOLVE, resolveAdapter);
            // WORK_ITEMS, FINALIZE는 의도적으로 미등록

            SqsPollingScheduler scheduler = new SqsPollingScheduler(adapters);

            assertThatCode(() -> {
                scheduler.pollWorkItemsQueue();
                scheduler.pollFinalizeQueue();
            }).doesNotThrowAnyException();

            verifyNoInteractions(resolveAdapter);
        }

        @Test
        @DisplayName("poll() 예외 시 scheduler가 중단되지 않는다")
        void pollExceptionDoesNotCrashScheduler() {
            SqsInboundPollingAdapter failingAdapter = mock(SqsInboundPollingAdapter.class);
            doThrow(new RuntimeException("simulated SQS failure")).when(failingAdapter).poll();

            Map<TestRunQueue, SqsInboundPollingAdapter> adapters = new EnumMap<>(TestRunQueue.class);
            adapters.put(TestRunQueue.WORK_ITEMS, failingAdapter);

            SqsPollingScheduler scheduler = new SqsPollingScheduler(adapters);

            // 예외가 전파되지 않아야 한다
            assertThatCode(() -> {
                scheduler.pollWorkItemsQueue();
                scheduler.pollWorkItemsQueue();
            }).doesNotThrowAnyException();

            verify(failingAdapter, times(2)).poll();
        }

        @Test
        @DisplayName("각 queue별 메서드가 올바른 adapter에만 위임한다")
        void eachQueueMethodDelegatesToCorrectAdapterOnly() {
            SqsInboundPollingAdapter resolveAdapter = mock(SqsInboundPollingAdapter.class);
            SqsInboundPollingAdapter workItemsAdapter = mock(SqsInboundPollingAdapter.class);
            SqsInboundPollingAdapter finalizeAdapter = mock(SqsInboundPollingAdapter.class);

            Map<TestRunQueue, SqsInboundPollingAdapter> adapters = new EnumMap<>(TestRunQueue.class);
            adapters.put(TestRunQueue.RESOLVE, resolveAdapter);
            adapters.put(TestRunQueue.WORK_ITEMS, workItemsAdapter);
            adapters.put(TestRunQueue.FINALIZE, finalizeAdapter);

            SqsPollingScheduler scheduler = new SqsPollingScheduler(adapters);

            scheduler.pollResolveQueue();

            verify(resolveAdapter, times(1)).poll();
            verifyNoInteractions(workItemsAdapter);
            verifyNoInteractions(finalizeAdapter);
        }
    }

    // ─── Helper ──────────────────────────────────────────────────────

    private static AnnotationConfigApplicationContext createContext(
            Map<String, String> properties,
            Class<?>... configClasses
    ) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.copyOf(properties))
        );
        context.register(configClasses);
        context.refresh();
        return context;
    }

    // ─── Test Configurations ────────────────────────────────────────

    @Configuration
    static class StubAdaptersConfig {
        @Bean
        Map<TestRunQueue, SqsInboundPollingAdapter> sqsPollingAdapters() {
            return new EnumMap<>(TestRunQueue.class);
        }
    }
}
