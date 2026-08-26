package com.guardbench.testrun.infrastructure.messaging;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.guardbench.testrun.application.OutboxPublisher;

/**
 * PENDING Outbox event를 주기적으로 SQS로 발행하는 스케줄러다.
 *
 * <p>ADR 0005 Outbox 계약의 운영 호출 경로다.
 * 접수 시 저장된 {@code TestRunRequested}와 Worker가 저장한 후속 event가
 * 이 스케줄러를 통해 queue로 이동한다.
 *
 * <p>{@code guardbench.sqs.enabled=true}일 때만 활성화한다.
 * 발행 간 지연은 {@code guardbench.sqs.outbox.delay-ms}(기본 1초)로 제어한다.
 */
@Component
@ConditionalOnProperty(name = "guardbench.sqs.enabled", havingValue = "true")
class OutboxPublishScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublishScheduler.class);

    private final OutboxPublisher outboxPublisher;
    private final int batchSize;

    OutboxPublishScheduler(OutboxPublisher outboxPublisher, SqsProperties properties) {
        this.outboxPublisher = Objects.requireNonNull(outboxPublisher);
        this.batchSize = properties.outbox().batchSize();
    }

    @Scheduled(
            fixedDelayString = "${guardbench.sqs.outbox.delay-ms:1000}",
            initialDelayString = "${guardbench.sqs.outbox.initial-delay-ms:1000}"
    )
    void publishPendingOutbox() {
        try {
            outboxPublisher.publishPending(batchSize);
        } catch (Exception exception) {
            // 발행 실패 event는 PENDING으로 남아 다음 주기에 같은 eventId로 재발행된다.
            log.error("Unexpected error publishing pending outbox events. Will retry next cycle.", exception);
        }
    }
}
