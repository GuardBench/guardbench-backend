package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.PublishBatchResult;
import com.guardbench.testrun.application.port.out.SqsPublishPort;

class OutboxPublisherTest {

    @Test
    @DisplayName("성공한 event만 event type queue로 발행하고 PUBLISHED로 전환한다")
    void publishesOnlySuccessfulEvents() {
        OutboxEventRecord resolveEvent = event("TestRunRequested", 1);
        OutboxEventRecord finalizeEvent = event("TestExecutionCompleted", 2);
        FakeOutbox outbox = new FakeOutbox(resolveEvent, finalizeEvent);
        // RESOLVE queue는 성공, FINALIZE queue는 실패
        FakeSqsPublisher sqs = new FakeSqsPublisher(Set.of(resolveEvent.eventId()));

        int published = new OutboxPublisher(outbox, sqs).publishPending(10);

        assertEquals(1, published);
        assertEquals(Set.of(TestRunQueue.RESOLVE, TestRunQueue.FINALIZE), sqs.queriedQueues);
        assertEquals(Set.of(resolveEvent.eventId()), outbox.publishedIds);
    }

    @Test
    @DisplayName("같은 queue로 묶인 event는 한 번의 batch 호출로 발행한다")
    void groupsEventsByQueueIntoSingleBatchCall() {
        OutboxEventRecord first = event("TestRunRequested", 1);
        OutboxEventRecord second = event("TestRunRequested", 2);
        FakeOutbox outbox = new FakeOutbox(first, second);
        FakeSqsPublisher sqs = new FakeSqsPublisher(Set.of(first.eventId(), second.eventId()));

        int published = new OutboxPublisher(outbox, sqs).publishPending(10);

        assertEquals(2, published);
        assertEquals(1, sqs.batchCallCount);
        assertEquals(2, sqs.lastEntries.size());
    }

    @Test
    @DisplayName("한 event의 발행 실패가 같은 batch의 나머지 event 처리를 막지 않는다")
    void partialBatchFailureDoesNotBlockOtherEvents() {
        OutboxEventRecord succeeded = event("TestRunRequested", 1);
        OutboxEventRecord failed = event("TestRunRequested", 2);
        FakeOutbox outbox = new FakeOutbox(succeeded, failed);
        FakeSqsPublisher sqs = new FakeSqsPublisher(Set.of(succeeded.eventId()));

        int published = new OutboxPublisher(outbox, sqs).publishPending(10);

        assertEquals(1, published);
        assertEquals(Set.of(succeeded.eventId()), outbox.publishedIds);
    }

    @Test
    @DisplayName("같은 queue로 11개가 넘어가면 SQS batch 한도(10)에 맞춰 청크로 나눠 발행한다")
    void chunksEventsExceedingSqsBatchLimit() {
        List<OutboxEventRecord> events = new ArrayList<>();
        for (int index = 0; index < 11; index++) {
            events.add(event("TestRunRequested", index + 1));
        }
        Set<UUID> allIds = events.stream().map(OutboxEventRecord::eventId).collect(java.util.stream.Collectors.toSet());
        FakeOutbox outbox = new FakeOutbox(events.toArray(OutboxEventRecord[]::new));
        FakeSqsPublisher sqs = new FakeSqsPublisher(allIds);

        int published = new OutboxPublisher(outbox, sqs).publishPending(11);

        assertEquals(11, published);
        assertEquals(2, sqs.batchCallCount);
        assertTrue(sqs.allEntrySizes.stream().allMatch(size -> size <= 10));
    }

    @Test
    @DisplayName("batch size는 양수여야 한다")
    void rejectsNonPositiveBatchSize() {
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxPublisher(new FakeOutbox(), new FakeSqsPublisher(Set.of())).publishPending(0));
    }

    private static OutboxEventRecord event(String eventType, long id) {
        UUID eventId = UUID.randomUUID();
        String suffix = eventType.equals("TestRunRequested") ? "" : ",\"snapshotId\":%d".formatted(id);
        String payload = "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"schemaVersion\":2,\"testRunId\":%d,\"occurredAt\":\"2026-08-26T00:00:00Z\"%s}"
                .formatted(eventId, eventType, id, suffix);
        return OutboxEventRecord.pending(eventId, eventType, payload, eventType + ":" + id, Instant.parse("2026-08-26T00:00:00Z"));
    }

    private static final class FakeOutbox implements OutboxPort {
        private final List<OutboxEventRecord> events;
        private final Set<UUID> publishedIds = new HashSet<>();

        private FakeOutbox(OutboxEventRecord... events) {
            this.events = List.of(events);
        }

        @Override public void save(OutboxEventRecord event) { throw new UnsupportedOperationException(); }
        @Override public List<OutboxEventRecord> findPendingBatch(int batchSize) { return events.stream().limit(batchSize).toList(); }
        @Override public void markPublished(Collection<UUID> eventIds) { publishedIds.addAll(eventIds); }
    }

    private static final class FakeSqsPublisher implements SqsPublishPort {
        private final Set<UUID> succeededEventIds;
        private final Set<TestRunQueue> queriedQueues = new HashSet<>();
        private final List<Integer> allEntrySizes = new ArrayList<>();
        private List<PublishBatchEntry> lastEntries;
        private int batchCallCount;

        private FakeSqsPublisher(Set<UUID> succeededEventIds) {
            this.succeededEventIds = succeededEventIds;
        }

        @Override
        public PublishBatchResult publishBatch(TestRunQueue queue, List<PublishBatchEntry> entries) {
            queriedQueues.add(queue);
            lastEntries = entries;
            allEntrySizes.add(entries.size());
            batchCallCount++;

            Set<UUID> succeeded = entries.stream()
                    .map(PublishBatchEntry::eventId)
                    .filter(succeededEventIds::contains)
                    .collect(java.util.stream.Collectors.toSet());
            return new PublishBatchResult(succeeded);
        }
    }
}
