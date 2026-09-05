package com.guardbench.testrun.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.guardbench.common.support.fixture.LogCapture;
import com.guardbench.testrun.application.ExecuteTestRunService;
import com.guardbench.testrun.application.messaging.TestExecutionRequestedMessage;
import com.guardbench.testrun.application.messaging.TestRunMessageCodec;
import com.guardbench.testrun.application.messaging.TestRunQueue;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.json.JsonMapper;

/** Issue #214: SQS metadata diagnostics must not change dispatch or acknowledgement. */
class SqsInboundPollingAdapterTest {
    private SqsClient sqs;
    private ExecuteTestRunService executeService;
    private SqsInboundPollingAdapter adapter;
    private LogCapture capture;
    private final TestRunMessageCodec codec = new TestRunMessageCodec(JsonMapper.builder().build());

    @BeforeEach
    void setUp() {
        sqs = mock(SqsClient.class);
        executeService = mock(ExecuteTestRunService.class);
        adapter = new SqsInboundPollingAdapter(sqs, codec, "https://sqs.example/workitems", TestRunQueue.WORK_ITEMS,
                new SqsProperties.Polling(10, 0, 90), null, executeService, null);
        capture = LogCapture.attach(SqsInboundPollingAdapter.class);
        when(executeService.execute(anyLong())).thenReturn(ExecuteTestRunService.ExecutionOutcome.EXECUTED);
    }

    @AfterEach
    void tearDown() {
        capture.detach();
    }

    @Test
    @DisplayName("SQS SentTimestamp부터 batch 수신까지의 queue wait를 실행 전에 기록한다")
    void logsQueueWaitBeforeDispatchUsingSqsTimestamp() {
        long sent = Instant.now().minusSeconds(68).toEpochMilli();
        Message message = message(100, Long.toString(sent));
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(message).build());
        when(executeService.execute(100)).thenAnswer(invocation -> {
            assertTrue(capture.hasMessageContaining("WorkItem 수신 timing"));
            return ExecuteTestRunService.ExecutionOutcome.EXECUTED;
        });

        assertEquals(1, adapter.poll());

        String timing = capture.firstMessageContaining("WorkItem 수신 timing");
        Instant received = Instant.parse(field(timing, "receivedAt"));
        assertEquals(received.toEpochMilli() - sent, Long.parseLong(field(timing, "queueWaitMs")));
        assertTrue(timing.contains("testRunId=42 snapshotId=100"));
        assertTrue(timing.contains("messageId=message-100"));
        assertTrue(timing.contains("eventId="));
        ArgumentCaptor<ReceiveMessageRequest> request = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(sqs).receiveMessage(request.capture());
        assertTrue(request.getValue().messageSystemAttributeNames().contains(MessageSystemAttributeName.SENT_TIMESTAMP));
        verify(sqs).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("앞 WorkItem 처리 시간이 뒤 WorkItem queue wait에 합산되지 않는다")
    void batchMessagesShareReceiveTimestamp() {
        String sent = Long.toString(Instant.now().minusSeconds(1).toEpochMilli());
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(
                ReceiveMessageResponse.builder().messages(message(100, sent), message(101, sent)).build());
        when(executeService.execute(100)).thenAnswer(invocation -> {
            java.util.concurrent.locks.LockSupport.parkNanos(20_000_000);
            return ExecuteTestRunService.ExecutionOutcome.EXECUTED;
        });

        assertEquals(2, adapter.poll());

        List<String> timings = capture.messages().stream().filter(m -> m.contains("WorkItem 수신 timing")).toList();
        assertEquals(2, timings.size());
        assertEquals(field(timings.get(0), "receivedAt"), field(timings.get(1), "receivedAt"));
        assertEquals(field(timings.get(0), "queueWaitMs"), field(timings.get(1), "queueWaitMs"));
    }

    @Test
    @DisplayName("누락·비정상·미래 timestamp는 queue wait를 만들지 않고 정상 ack를 유지한다")
    void invalidTimestampDoesNotFailDispatchOrLeakRawMetadata() {
        for (String timestamp : new String[]{null, "Authorization: secret\nforged", "-1", Long.toString(Long.MAX_VALUE)}) {
            when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(
                    ReceiveMessageResponse.builder().messages(message(100, timestamp)).build());
            assertEquals(1, adapter.poll());
        }

        List<String> timings = capture.messages().stream().filter(m -> m.contains("WorkItem 수신 timing")).toList();
        assertEquals(4, timings.size());
        for (String timing : timings) {
            assertTrue(timing.contains("queueWaitMs=null"));
            assertFalse(timing.contains("secret"));
            assertFalse(timing.contains("Authorization"));
        }
        verify(executeService, times(4)).execute(100);
        verify(sqs, times(4)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    @DisplayName("재전달 WorkItem도 수신 timing을 남기면서 기존 nack 결과를 유지한다")
    void redeliveryKeepsNackAndOriginalSentTimestamp() {
        Message message = message(100, Long.toString(Instant.now().minusSeconds(90).toEpochMilli()));
        when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(
                ReceiveMessageResponse.builder().messages(message).build());
        when(executeService.execute(100)).thenReturn(ExecuteTestRunService.ExecutionOutcome.CLAIM_HELD_BY_OTHER);

        adapter.poll();
        adapter.poll();

        assertEquals(2, capture.messages().stream().filter(m -> m.contains("WorkItem 수신 timing")).count());
        verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    private Message message(long snapshotId, String timestamp) {
        String payload = codec.encode(new TestExecutionRequestedMessage(UUID.randomUUID(), 42, snapshotId,
                Instant.parse("2026-01-01T00:00:00Z")));
        return Message.builder().messageId("message-" + snapshotId).receiptHandle("receipt-" + snapshotId)
                .body(payload).attributes(timestamp == null ? Map.of() : Map.of(MessageSystemAttributeName.SENT_TIMESTAMP, timestamp))
                .build();
    }

    private static String field(String message, String name) {
        return message.split(name + "=", 2)[1].split(" ", 2)[0];
    }
}
