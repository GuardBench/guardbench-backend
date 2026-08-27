package com.guardbench.testrun.infrastructure.messaging;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.guardbench.testrun.application.messaging.TestRunQueue;

/**
 * Worker 모드에서 SQS 폴링 어댑터를 주기적으로 호출하는 스케줄러다.
 *
 * <p>{@code guardbench.worker.enabled=true}일 때
 * {@link SqsWorkerConfiguration}이 등록한 어댑터 Map을 주입받아
 * 각 queue에 대해 {@code poll()}을 호출한다.
 *
 * <p>poll 간 지연은 {@code guardbench.sqs.polling.delay-ms}(기본 1초)로 제어하며,
 * SQS long polling과 함께 back-pressure 역할을 한다.
 */
@Component
@ConditionalOnProperty(name = "guardbench.worker.enabled", havingValue = "true")
class SqsPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(SqsPollingScheduler.class);

    private final Map<TestRunQueue, SqsInboundPollingAdapter> adapters;

    SqsPollingScheduler(Map<TestRunQueue, SqsInboundPollingAdapter> adapters) {
        this.adapters = adapters;
    }

    @Scheduled(fixedDelayString = "${guardbench.sqs.polling.delay-ms:1000}")
    void pollResolveQueue() {
        pollSafely(TestRunQueue.RESOLVE);
    }

    @Scheduled(fixedDelayString = "${guardbench.sqs.polling.delay-ms:1000}")
    void pollWorkItemsQueue() {
        pollSafely(TestRunQueue.WORK_ITEMS);
    }

    @Scheduled(fixedDelayString = "${guardbench.sqs.polling.delay-ms:1000}")
    void pollFinalizeQueue() {
        pollSafely(TestRunQueue.FINALIZE);
    }

    private void pollSafely(TestRunQueue queue) {
        SqsInboundPollingAdapter adapter = adapters.get(queue);
        if (adapter == null) {
            return;
        }
        try {
            adapter.poll();
        } catch (Exception exception) {
            log.error("Unexpected error polling {}. Will retry next cycle.", queue.queueName(), exception);
        }
    }
}
