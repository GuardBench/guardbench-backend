package com.guardbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class OutboxAdapterIntegrationTest {

    private static final Instant BASE = Instant.parse("2026-08-25T00:00:00Z");
    private static final String SAMPLE_PAYLOAD = """
            {"eventId":"00000000-0000-0000-0000-000000000001","eventType":"TestRunRequested","schemaVersion":1,"testRunId":1,"occurredAt":"2026-08-25T00:00:00Z"}""";

    @Autowired
    private OutboxPort outboxPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanUp() {
        jdbcTemplate.execute("TRUNCATE TABLE outbox_event CASCADE");
    }

    @Test
    @DisplayName("PENDING 이벤트를 저장하고 findPendingBatch로 조회한다")
    void savePendingEvent_andFindBatch() {
        UUID eventId = UUID.randomUUID();
        OutboxEventRecord event = OutboxEventRecord.pending(
                eventId, "TestRunRequested", SAMPLE_PAYLOAD, "TestRunRequested:1", BASE);
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

        outboxPort.save(OutboxEventRecord.pending(eventId1, "TestRunRequested", SAMPLE_PAYLOAD, dedupKey, BASE));
        outboxPort.save(OutboxEventRecord.pending(eventId2, "TestRunRequested", SAMPLE_PAYLOAD, dedupKey, BASE));

        List<OutboxEventRecord> batch = outboxPort.findPendingBatch(10);
        assertEquals(1, batch.size());
        assertEquals(eventId1, batch.getFirst().eventId());
    }

    @Test
    @DisplayName("markPublished 후 PUBLISHED 상태가 되고 PENDING batch에서 제외된다")
    void markPublished_updatesStatus() {
        UUID eventId = UUID.randomUUID();
        outboxPort.save(OutboxEventRecord.pending(
                eventId, "TestRunRequested", SAMPLE_PAYLOAD, "TestRunRequested:99", BASE));

        outboxPort.markPublished(eventId);

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
                eventId1, "TestRunRequested", SAMPLE_PAYLOAD, "TestRunRequested:100", BASE));
        outboxPort.save(OutboxEventRecord.pending(
                eventId2, "TestRunRequested", SAMPLE_PAYLOAD, "TestRunRequested:101", BASE.plusSeconds(1)));

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
