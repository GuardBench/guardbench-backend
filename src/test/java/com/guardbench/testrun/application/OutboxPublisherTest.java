package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.SqsPublishPort;

class OutboxPublisherTest {

    @Test
    @DisplayName("성공한 event만 event type queue로 발행하고 PUBLISHED로 전환한다")
    void publishesOnlySuccessfulEvents() {
        FakeOutbox outbox = new FakeOutbox(event("TestRunRequested", 1), event("TestExecutionCompleted", 2));
        FakeSqsPublisher sqs = new FakeSqsPublisher(true, false);

        int published = new OutboxPublisher(outbox, sqs).publishPending(10);

        assertEquals(1, published);
        assertEquals(List.of(TestRunQueue.RESOLVE, TestRunQueue.FINALIZE), sqs.queues);
        assertEquals(List.of(outbox.events.getFirst().eventId()), outbox.publishedIds);
    }

    @Test
    @DisplayName("batch size는 양수여야 한다")
    void rejectsNonPositiveBatchSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxPublisher(new FakeOutbox(), new FakeSqsPublisher()).publishPending(0));
    }

    private static OutboxEventRecord event(String eventType, long id) {
        UUID eventId = UUID.randomUUID();
        String suffix = eventType.equals("TestRunRequested") ? "" : ",\"snapshotId\":%d,\"targetType\":\"BASELINE\"".formatted(id);
        String payload = "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"schemaVersion\":1,\"testRunId\":%d,\"occurredAt\":\"2026-08-26T00:00:00Z\"%s}"
                .formatted(eventId, eventType, id, suffix);
        return OutboxEventRecord.pending(eventId, eventType, payload, eventType + ":" + id, Instant.parse("2026-08-26T00:00:00Z"));
    }

    private static final class FakeOutbox implements OutboxPort {
        private final List<OutboxEventRecord> events;
        private final List<UUID> publishedIds = new ArrayList<>();

        private FakeOutbox(OutboxEventRecord... events) {
            this.events = List.of(events);
        }

        @Override public void save(OutboxEventRecord event) { throw new UnsupportedOperationException(); }
        @Override public List<OutboxEventRecord> findPendingBatch(int batchSize) { return events.stream().limit(batchSize).toList(); }
        @Override public void markPublished(UUID eventId) { publishedIds.add(eventId); }
    }

    private static final class FakeSqsPublisher implements SqsPublishPort {
        private final List<Boolean> outcomes;
        private final List<TestRunQueue> queues = new ArrayList<>();
        private int index;

        private FakeSqsPublisher(Boolean... outcomes) { this.outcomes = List.of(outcomes); }
        @Override public boolean publish(TestRunQueue queue, String payload) {
            queues.add(queue);
            return outcomes.isEmpty() || outcomes.get(index++);
        }
    }
}
