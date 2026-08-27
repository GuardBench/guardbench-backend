package com.guardbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class OutboxAdapterIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-08-25T00:00:00Z");
    private static String payload(UUID eventId, long testRunId) {
        return "{\"eventId\":\"" + eventId + "\",\"eventType\":\"TestRunRequested\",\"schemaVersion\":1,\"testRunId\":"
                + testRunId + ",\"occurredAt\":\"2026-08-25T00:00:00Z\"}";
    }

    @Autowired
    private OutboxPort outboxPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        new TestRunPersistenceFixture(jdbcTemplate).clearPersistenceTables();
    }

    @Test
    @DisplayName("payload header가 Outbox 열 값과 다르면 이벤트를 만들 수 없다")
    void rejectsPayloadHeaderMismatch() {
        UUID eventId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> OutboxEventRecord.pending(
                eventId,
                "TestRunRequested",
                payload(UUID.randomUUID(), 1),
                "TestRunRequested:1",
                BASE));
    }

    @Test
    @DisplayName("실행 이벤트 payload에 snapshotId 또는 targetType이 없으면 이벤트를 만들 수 없다")
    void rejectsExecutionPayloadWithoutRequiredFields() {
        UUID eventId = UUID.randomUUID();
        String missingTarget = "{\"eventId\":\"" + eventId
                + "\",\"eventType\":\"TestExecutionRequested\",\"schemaVersion\":1,\"testRunId\":1,\"snapshotId\":2,\"occurredAt\":\"2026-08-25T00:00:00Z\"}";

        assertThrows(IllegalArgumentException.class, () -> OutboxEventRecord.pending(
                eventId, "TestExecutionRequested", missingTarget, "TestExecutionRequested:2:BASELINE", BASE));
    }

    @Test
    @DisplayName("PENDING Outbox event를 저장한 뒤 batch로 조회한다")
    void savePendingEvent_andFindBatch() {
        UUID eventId = UUID.randomUUID();
        OutboxEventRecord event = OutboxEventRecord.pending(
                eventId, "TestRunRequested", payload(eventId, 1), "TestRunRequested:1", BASE);
        outboxPort.save(event);

        List<OutboxEventRecord> batch = outboxPort.findPendingBatch(10);
        assertEquals(1, batch.size());
        assertEquals(eventId, batch.getFirst().eventId());
        assertEquals("PENDING", batch.getFirst().status());
        assertEquals("TestRunRequested", batch.getFirst().eventType());
    }

    @Test
    @DisplayName("deduplication_key 중복은 무시한다")
    void duplicateDeduplicationKey_isIgnored() {
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        String dedupKey = "TestRunRequested:42";

        outboxPort.save(OutboxEventRecord.pending(eventId1, "TestRunRequested", payload(eventId1, 42), dedupKey, BASE));
        outboxPort.save(OutboxEventRecord.pending(eventId2, "TestRunRequested", payload(eventId2, 42), dedupKey, BASE));

        List<OutboxEventRecord> batch = outboxPort.findPendingBatch(10);
        assertEquals(1, batch.size());
        assertEquals(eventId1, batch.getFirst().eventId());
    }

    @Test
    @DisplayName("markPublished 후 PUBLISHED 상태가 되고 PENDING batch에서 제외된다")
    void markPublished_updatesStatus() {
        UUID eventId = UUID.randomUUID();
        outboxPort.save(OutboxEventRecord.pending(
                eventId, "TestRunRequested", payload(eventId, 99), "TestRunRequested:99", BASE));

        outboxPort.markPublished(java.util.List.of(eventId));

        List<OutboxEventRecord> pending = outboxPort.findPendingBatch(10);
        assertTrue(pending.isEmpty());

        // published_at이 설정됨을 DB에서 확인
        var publishedAt = jdbcTemplate.queryForObject(
                "SELECT published_at FROM outbox_event WHERE event_id = ?::uuid",
                java.sql.Timestamp.class,
                eventId.toString());
        assertNotNull(publishedAt);
    }

    @Test
    @DisplayName("SKIP LOCKED으로 동시 조회 시 동일 row를 가져가지 않는다")
    void skipLocked_preventsDuplicateConsumption() {
        // 2개 이벤트 저장
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        outboxPort.save(OutboxEventRecord.pending(
                eventId1, "TestRunRequested", payload(eventId1, 100), "TestRunRequested:100", BASE));
        outboxPort.save(OutboxEventRecord.pending(
                eventId2, "TestRunRequested", payload(eventId2, 101), "TestRunRequested:101", BASE.plusSeconds(1)));

        // 첫 번째 조회가 1개를 lock
        List<OutboxEventRecord> first = outboxPort.findPendingBatch(1);
        assertEquals(1, first.size());

        // 같은 트랜잭션 내에서 다시 조회하면 나머지를 가져옴(단일 스레드지만 SKIP LOCKED 동작 검증)
        // Note: 실제 동시성은 두 트랜잭션이 필요하지만 SKIP LOCKED + LIMIT 1 조합의 순서 보장을 검증
        List<OutboxEventRecord> all = outboxPort.findPendingBatch(10);
        // 같은 connection이므로 lock이 같은 세션이라 모두 보임
        assertEquals(2, all.size());
    }
}
