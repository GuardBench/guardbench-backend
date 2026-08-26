package com.guardbench.testrun.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.guardbench.testrun.application.OutboxPublisher;

/**
 * Outbox 발행 스케줄러의 조건부 등록과 위임 동작을 검증한다.
 *
 * <p>DB, SQS 없이 가벼운 AnnotationConfigApplicationContext와 test double로 검증한다.
 */
class OutboxPublishSchedulerTest {

    @Test
    @DisplayName("guardbench.sqs.enabled=false이면 OutboxPublishScheduler 빈이 없다")
    void schedulerNotRegisteredWhenSqsDisabled() {
        try (var context = createContext(Map.of("guardbench.sqs.enabled", "false"))) {
            assertThat(context.getBeanNamesForType(OutboxPublishScheduler.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("guardbench.sqs.enabled 미설정이면 OutboxPublishScheduler 빈이 없다")
    void schedulerNotRegisteredWhenPropertyMissing() {
        try (var context = createContext(Map.of())) {
            assertThat(context.getBeanNamesForType(OutboxPublishScheduler.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("설정된 batch size로 publishPending에 위임한다")
    void delegatesWithConfiguredBatchSize() {
        OutboxPublisher publisher = mock(OutboxPublisher.class);
        when(publisher.publishPending(anyInt())).thenReturn(3);

        new OutboxPublishScheduler(publisher, propertiesWithBatchSize(5)).publishPendingOutbox();

        verify(publisher, times(1)).publishPending(5);
    }

    @Test
    @DisplayName("발행 중 예외가 발생해도 스케줄러가 중단되지 않는다")
    void publishFailureDoesNotCrashScheduler() {
        OutboxPublisher publisher = mock(OutboxPublisher.class);
        when(publisher.publishPending(anyInt())).thenThrow(new RuntimeException("simulated publish failure"));

        OutboxPublishScheduler scheduler = new OutboxPublishScheduler(publisher, propertiesWithBatchSize(10));

        assertThatCode(() -> {
            scheduler.publishPendingOutbox();
            scheduler.publishPendingOutbox();
        }).doesNotThrowAnyException();

        verify(publisher, times(2)).publishPending(10);
    }

    private static SqsProperties propertiesWithBatchSize(int batchSize) {
        return new SqsProperties(null, null, null, null, new SqsProperties.Outbox(batchSize));
    }

    private static AnnotationConfigApplicationContext createContext(Map<String, String> properties) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(
                new MapPropertySource("test", Map.copyOf(properties))
        );
        context.register(OutboxPublishScheduler.class);
        context.refresh();
        return context;
    }
}
