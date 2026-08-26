package com.guardbench.testrun.application;

import java.util.Objects;

import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.SqsPublishPort;

/**
 * PENDING Outbox를 ADR 0005의 event type별 SQS queue로 발행한다.
 * SQS 발행에 실패한 event는 PENDING으로 남겨 다음 poll에서 같은 eventId로 재발행한다.
 *
 * <p>{@link #publishPending(int)}는 하나의 트랜잭션으로 실행된다.
 * {@code findPendingBatch}의 {@code SELECT ... FOR UPDATE SKIP LOCKED} row lock을
 * SQS 전송과 {@code markPublished}까지 유지해야 병렬 Publisher가 같은 PENDING batch를
 * 중복 발행하지 않는다. 이 때문에 SQS 전송은 트랜잭션 안에서 수행하며,
 * lock 보유 시간을 제한하기 위해 batch size를 작게 유지한다.
 *
 * <p>스케줄러 등 외부 호출자가 이 메서드를 직접 호출해야 트랜잭션 프록시가 적용된다.
 */
public class OutboxPublisher {

    private final OutboxPort outboxPort;
    private final SqsPublishPort sqsPublishPort;

    public OutboxPublisher(OutboxPort outboxPort, SqsPublishPort sqsPublishPort) {
        this.outboxPort = Objects.requireNonNull(outboxPort, "outboxPort must not be null");
        this.sqsPublishPort = Objects.requireNonNull(sqsPublishPort, "sqsPublishPort must not be null");
    }

    @Transactional
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
