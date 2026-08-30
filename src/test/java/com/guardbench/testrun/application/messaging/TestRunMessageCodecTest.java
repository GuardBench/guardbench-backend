package com.guardbench.testrun.application.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

class TestRunMessageCodecTest {

    private final TestRunMessageCodec codec = new TestRunMessageCodec(new ObjectMapper());
    private final Instant occurredAt = Instant.parse("2026-08-26T00:00:00Z");

    @Test
    @DisplayName("TestRunRequested v2 메시지를 round-trip한다")
    void roundTripsTestRunRequested() {
        TestRunRequestedMessage original = new TestRunRequestedMessage(UUID.randomUUID(), 41L, occurredAt);

        TestRunMessage decoded = codec.decode(codec.encode(original));

        assertEquals(original, decoded);
        assertEquals(TestRunQueue.RESOLVE, TestRunQueue.forEventType(decoded.eventType()));
    }

    @Test
    @DisplayName("실행 요청과 완료 메시지의 Snapshot 값을 role 없이 round-trip한다")
    void roundTripsExecutionMessages() {
        TestExecutionRequestedMessage requested = new TestExecutionRequestedMessage(
                UUID.randomUUID(), 41L, 42L, occurredAt);
        TestExecutionCompletedMessage completed = new TestExecutionCompletedMessage(
                UUID.randomUUID(), 41L, 42L, occurredAt);

        assertEquals(requested, codec.decode(codec.encode(requested)));
        assertEquals(completed, codec.decode(codec.encode(completed)));
        assertEquals(TestRunQueue.WORK_ITEMS, TestRunQueue.forEventType(requested.eventType()));
        assertEquals(TestRunQueue.FINALIZE, TestRunQueue.forEventType(completed.eventType()));
    }

    @Test
    @DisplayName("알 수 없는 optional field는 무시한다")
    void ignoresUnknownOptionalField() {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","eventType":"TestRunRequested","schemaVersion":2,
                "testRunId":41,"occurredAt":"2026-08-26T00:00:00Z","futureField":"ignored"}
                """.formatted(eventId);

        TestRunMessage message = codec.decode(payload);

        assertInstanceOf(TestRunRequestedMessage.class, message);
        assertEquals(eventId, message.eventId());
    }

    @Test
    @DisplayName("필수 field 누락, 지원하지 않는 schema와 event type은 역직렬화를 거부한다")
    void rejectsInvalidV2Messages() {
        assertThrows(InvalidTestRunMessageException.class,
                () -> codec.decode("{\"eventType\":\"TestRunRequested\",\"schemaVersion\":2}"));
        assertThrows(InvalidTestRunMessageException.class,
                () -> codec.decode("{\"eventId\":\"%s\",\"eventType\":\"TestRunRequested\",\"schemaVersion\":1,\"testRunId\":1,\"occurredAt\":\"2026-08-26T00:00:00Z\"}"
                        .formatted(UUID.randomUUID())));
        assertThrows(InvalidTestRunMessageException.class,
                () -> codec.decode("{\"eventId\":\"%s\",\"eventType\":\"Unknown\",\"schemaVersion\":2,\"testRunId\":1,\"occurredAt\":\"2026-08-26T00:00:00Z\"}"
                        .formatted(UUID.randomUUID())));
    }
}
