package com.guardbench.testrun.application;

import java.util.Objects;

import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.SqsPublishPort;

/**
 * PENDING Outbox를 ADR 0005의 event type별 SQS queue로 발행한다.
 * SQS 발행에 실패한 event는 PENDING으로 남겨 다음 poll에서 같은 eventId로 재발행한다.
 */
public class OutboxPublisher {

    private final OutboxPort outboxPort;
    private final SqsPublishPort sqsPublishPort;

    public OutboxPublisher(OutboxPort outboxPort, SqsPublishPort sqsPublishPort) {
        this.outboxPort = Objects.requireNonNull(outboxPort, "outboxPort must not be null");
        this.sqsPublishPort = Objects.requireNonNull(sqsPublishPort, "sqsPublishPort must not be null");
    }

    public int publishPending(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        int publishedCount = 0;
        for (var event : outboxPort.findPendingBatch(batchSize)) {
            TestRunQueue queue = TestRunQueue.forEventType(event.eventType());
            if (sqsPublishPort.publish(queue, event.payload())) {
                outboxPort.markPublished(event.eventId());
                publishedCount++;
            }
        }
        return publishedCount;
    }
}
