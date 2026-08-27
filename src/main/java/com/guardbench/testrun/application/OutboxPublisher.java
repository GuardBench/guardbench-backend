package com.guardbench.testrun.application;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.PublishBatchResult;
import com.guardbench.testrun.application.port.out.SqsPublishPort;
import com.guardbench.testrun.application.port.out.SqsPublishPort.PublishBatchEntry;

/**
 * PENDING Outbox를 ADR 0005의 event type별 SQS queue로 발행한다.
 *
 * <p>ADR 0005: 잠근 batch를 SQS {@code SendMessageBatch}로 발행하고 성공 항목만
 * {@code PUBLISHED}로 전환한다. 한 항목의 발행 실패가 같은 batch의 나머지 항목
 * 처리를 막지 않는다. SQS {@code SendMessageBatch}는 queue당 최대 10개 항목을
 * 지원하므로 이벤트를 목적 queue별로 그룹핑해 10개 단위 청크로 전송한다.
 * 발행에 실패한 event는 PENDING으로 남겨 다음 poll에서 같은 eventId로 재발행한다.
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

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    /** SQS SendMessageBatch가 queue당 지원하는 최대 항목 수. */
    private static final int SQS_BATCH_LIMIT = 10;

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

        List<OutboxEventRecord> pending = outboxPort.findPendingBatch(batchSize);
        if (pending.isEmpty()) {
            return 0;
        }

        Map<TestRunQueue, List<OutboxEventRecord>> byQueue = groupByQueue(pending);

        int publishedCount = 0;
        for (var entry : byQueue.entrySet()) {
            publishedCount += publishToQueue(entry.getKey(), entry.getValue());
        }
        return publishedCount;
    }

    private int publishToQueue(TestRunQueue queue, List<OutboxEventRecord> events) {
        int publishedCount = 0;
        for (int start = 0; start < events.size(); start += SQS_BATCH_LIMIT) {
            List<OutboxEventRecord> chunk = events.subList(start, Math.min(start + SQS_BATCH_LIMIT, events.size()));
            List<PublishBatchEntry> entries = chunk.stream()
                    .map(event -> new PublishBatchEntry(event.eventId(), event.payload()))
                    .toList();

            chunk.forEach(event -> logPublishStart(queue, event));

            PublishBatchResult result = sqsPublishPort.publishBatch(queue, entries);

            List<UUID> succeededIds = chunk.stream()
                    .map(OutboxEventRecord::eventId)
                    .filter(result::succeeded)
                    .toList();
            outboxPort.markPublished(succeededIds);
            publishedCount += succeededIds.size();
            chunk.forEach(event -> logPublishResult(queue, event, result.succeeded(event.eventId())));
        }
        return publishedCount;
    }

    private static void logPublishStart(TestRunQueue queue, OutboxEventRecord event) {
        OutboxEventRecord.ObservabilityContext context = event.observabilityContext();
        log.info("Outbox event publish started. queue={} eventId={} eventType={} testRunId={} snapshotId={} targetType={}",
                queue.queueName(), event.eventId(), event.eventType(), context.testRunId(),
                context.snapshotId(), context.targetType());
    }

    private static void logPublishResult(TestRunQueue queue, OutboxEventRecord event, boolean succeeded) {
        OutboxEventRecord.ObservabilityContext context = event.observabilityContext();
        if (succeeded) {
            log.info("Outbox event published. queue={} eventId={} eventType={} testRunId={} snapshotId={} targetType={}",
                    queue.queueName(), event.eventId(), event.eventType(), context.testRunId(),
                    context.snapshotId(), context.targetType());
            return;
        }
        log.warn("Outbox event publish deferred for retry. queue={} eventId={} eventType={} testRunId={} snapshotId={} targetType={}",
                queue.queueName(), event.eventId(), event.eventType(), context.testRunId(),
                context.snapshotId(), context.targetType());
    }

    private static Map<TestRunQueue, List<OutboxEventRecord>> groupByQueue(List<OutboxEventRecord> events) {
        Map<TestRunQueue, List<OutboxEventRecord>> byQueue = new EnumMap<>(TestRunQueue.class);
        for (OutboxEventRecord event : events) {
            TestRunQueue queue = TestRunQueue.forEventType(event.eventType());
            byQueue.computeIfAbsent(queue, ignored -> new ArrayList<>()).add(event);
        }
        return byQueue;
    }
}
