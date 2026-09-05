package com.guardbench.testrun.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.guardbench.testrun.application.ExecuteTestRunService;
import com.guardbench.testrun.application.messaging.TestExecutionRequestedMessage;
import com.guardbench.testrun.application.messaging.TestRunMessageCodec;
import com.guardbench.testrun.application.messaging.TestRunQueue;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import tools.jackson.databind.json.JsonMapper;

class WorkItemBoundedConcurrencyTest {

    private final TestRunMessageCodec codec = new TestRunMessageCodec(JsonMapper.builder().build());

    @Test
    @DisplayName("동시성 2에서 서로 다른 WorkItem 두 개가 실제로 겹쳐 실행된다")
    void differentWorkItemsOverlapAtConfiguredConcurrency() throws Exception {
        SqsClient sqs = org.mockito.Mockito.mock(SqsClient.class);
        ExecuteTestRunService executeService = org.mockito.Mockito.mock(ExecuteTestRunService.class);
        WorkItemConcurrencyController controller = new WorkItemConcurrencyController(2, Duration.ofSeconds(1));
        try {
            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            when(executeService.execute(anyLong())).thenAnswer(invocation -> {
                started.countDown();
                assertTrue(release.await(2, TimeUnit.SECONDS));
                return ExecuteTestRunService.ExecutionOutcome.EXECUTED;
            });
            when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(
                    response(message(100), message(101)));

            SqsInboundPollingAdapter adapter = adapter(sqs, executeService, controller);

            assertEquals(2, adapter.poll());
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertEquals(2, controller.currentInFlight());
            verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));

            release.countDown();
            verify(sqs, timeout(2000).times(2)).deleteMessage(any(DeleteMessageRequest.class));
            assertTrue(awaitInFlight(controller, 0));
        } finally {
            controller.close();
        }
    }

    @Test
    @DisplayName("모든 slot이 사용 중이면 WorkItems ReceiveMessage를 호출하지 않는다")
    void doesNotReceiveWhenAllSlotsAreInFlight() throws Exception {
        SqsClient sqs = org.mockito.Mockito.mock(SqsClient.class);
        ExecuteTestRunService executeService = org.mockito.Mockito.mock(ExecuteTestRunService.class);
        WorkItemConcurrencyController controller = new WorkItemConcurrencyController(2, Duration.ofSeconds(1));
        try {
            CountDownLatch started = new CountDownLatch(2);
            CountDownLatch release = new CountDownLatch(1);
            when(executeService.execute(anyLong())).thenAnswer(invocation -> {
                started.countDown();
                release.await();
                return ExecuteTestRunService.ExecutionOutcome.EXECUTED;
            });
            when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                    .thenReturn(response(message(100), message(101)));

            SqsInboundPollingAdapter adapter = adapter(sqs, executeService, controller);

            adapter.poll();
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertEquals(0, adapter.poll());
            verify(sqs, times(1)).receiveMessage(any(ReceiveMessageRequest.class));

            release.countDown();
        } finally {
            controller.close();
        }
    }

    @Test
    @DisplayName("현재 in-flight가 1이면 다음 ReceiveMessage의 최대 수신량이 1이다")
    void requestsOnlyRemainingSlots() throws Exception {
        SqsClient sqs = org.mockito.Mockito.mock(SqsClient.class);
        ExecuteTestRunService executeService = org.mockito.Mockito.mock(ExecuteTestRunService.class);
        WorkItemConcurrencyController controller = new WorkItemConcurrencyController(2, Duration.ofSeconds(1));
        try {
            CountDownLatch firstStarted = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            when(executeService.execute(anyLong())).thenAnswer(invocation -> {
                firstStarted.countDown();
                release.await();
                return ExecuteTestRunService.ExecutionOutcome.EXECUTED;
            });
            when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                    .thenReturn(response(message(100)), response());

            SqsInboundPollingAdapter adapter = adapter(sqs, executeService, controller);

            assertEquals(1, adapter.poll());
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS));
            assertEquals(0, adapter.poll());

            ArgumentCaptor<ReceiveMessageRequest> requests = ArgumentCaptor.forClass(ReceiveMessageRequest.class);
            verify(sqs, times(2)).receiveMessage(requests.capture());
            assertEquals(2, requests.getAllValues().get(0).maxNumberOfMessages());
            assertEquals(1, requests.getAllValues().get(1).maxNumberOfMessages());

            release.countDown();
        } finally {
            controller.close();
        }
    }

    @Test
    @DisplayName("WorkItem 예외 후 slot을 반환하고 다음 메시지를 처리한다")
    void returnsSlotAfterExecutionFailure() throws Exception {
        SqsClient sqs = org.mockito.Mockito.mock(SqsClient.class);
        ExecuteTestRunService executeService = org.mockito.Mockito.mock(ExecuteTestRunService.class);
        WorkItemConcurrencyController controller = new WorkItemConcurrencyController(1, Duration.ofSeconds(1));
        try {
            CountDownLatch secondStarted = new CountDownLatch(1);
            CountDownLatch firstFinished = new CountDownLatch(1);
            AtomicInteger invocationCount = new AtomicInteger();
            when(executeService.execute(anyLong())).thenAnswer(invocation -> {
                if (invocationCount.getAndIncrement() == 0) {
                    try {
                        throw new IllegalStateException("simulated failure");
                    } finally {
                        firstFinished.countDown();
                    }
                }
                secondStarted.countDown();
                return ExecuteTestRunService.ExecutionOutcome.EXECUTED;
            });
            when(sqs.receiveMessage(any(ReceiveMessageRequest.class)))
                    .thenReturn(response(message(100)), response(message(101)));

            SqsInboundPollingAdapter adapter = adapter(sqs, executeService, controller);

            assertEquals(1, adapter.poll());
            assertTrue(firstFinished.await(2, TimeUnit.SECONDS));
            assertTrue(awaitInFlight(controller, 0));
            assertEquals(1, adapter.poll());
            assertTrue(secondStarted.await(2, TimeUnit.SECONDS));

            verify(sqs, timeout(2000).times(1)).deleteMessage(any(DeleteMessageRequest.class));
            assertTrue(awaitInFlight(controller, 0));
        } finally {
            controller.close();
        }
    }

    @Test
    @DisplayName("shutdown timeout이 지나도 완료되지 않은 WorkItem을 조기 ACK하지 않는다")
    void doesNotAcknowledgeUnfinishedWorkItemDuringShutdownTimeout() throws Exception {
        SqsClient sqs = org.mockito.Mockito.mock(SqsClient.class);
        ExecuteTestRunService executeService = org.mockito.Mockito.mock(ExecuteTestRunService.class);
        WorkItemConcurrencyController controller = new WorkItemConcurrencyController(1, Duration.ofMillis(50));
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            when(executeService.execute(anyLong())).thenAnswer(invocation -> {
                started.countDown();
                while (release.getCount() > 0) {
                    try {
                        release.await(10, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException ignored) {
                        // 종료 timeout 이후에도 실제 작업이 끝나기 전에는 ACK하지 않는다.
                    }
                }
                return ExecuteTestRunService.ExecutionOutcome.EXECUTED;
            });
            when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response(message(100)));

            SqsInboundPollingAdapter adapter = adapter(sqs, executeService, controller);
            adapter.poll();
            assertTrue(started.await(2, TimeUnit.SECONDS));

            controller.close();

            verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
            release.countDown();
            assertTrue(awaitInFlight(controller, 0));
            verify(sqs, never()).deleteMessage(any(DeleteMessageRequest.class));
        } finally {
            release.countDown();
            controller.close();
        }
    }

    @Test
    @DisplayName("ACK 중 shutdown timeout이 발생해도 DeleteMessage와 timeout 상태 전환이 교차하지 않는다")
    void serializesAcknowledgementWithShutdownTimeout() throws Exception {
        SqsClient sqs = org.mockito.Mockito.mock(SqsClient.class);
        ExecuteTestRunService executeService = org.mockito.Mockito.mock(ExecuteTestRunService.class);
        WorkItemConcurrencyController controller = new WorkItemConcurrencyController(1, Duration.ofMillis(50));
        CountDownLatch deleteStarted = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        CountDownLatch shutdownReturned = new CountDownLatch(1);
        Thread shutdownThread = null;
        try {
            when(executeService.execute(anyLong()))
                    .thenReturn(ExecuteTestRunService.ExecutionOutcome.EXECUTED);
            when(sqs.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response(message(100)));
            when(sqs.deleteMessage(any(DeleteMessageRequest.class))).thenAnswer(invocation -> {
                deleteStarted.countDown();
                releaseDelete.await();
                return DeleteMessageResponse.builder().build();
            });

            SqsInboundPollingAdapter adapter = adapter(sqs, executeService, controller);
            adapter.poll();
            assertTrue(deleteStarted.await(2, TimeUnit.SECONDS));

            shutdownThread = new Thread(() -> {
                controller.close();
                shutdownReturned.countDown();
            });
            shutdownThread.start();

            assertFalse(shutdownReturned.await(150, TimeUnit.MILLISECONDS));
            releaseDelete.countDown();
            assertTrue(shutdownReturned.await(2, TimeUnit.SECONDS));
            shutdownThread.join(2_000);
            verify(sqs, times(1)).deleteMessage(any(DeleteMessageRequest.class));
        } finally {
            releaseDelete.countDown();
            if (shutdownThread != null) {
                shutdownThread.join(2_000);
            }
            controller.close();
        }
    }

    private SqsInboundPollingAdapter adapter(
            SqsClient sqs,
            ExecuteTestRunService executeService,
            WorkItemConcurrencyController controller
    ) {
        return new SqsInboundPollingAdapter(
                sqs,
                codec,
                "https://sqs.example/workitems",
                TestRunQueue.WORK_ITEMS,
                new SqsProperties.Polling(10, 0, 90),
                null,
                executeService,
                null,
                controller
        );
    }

    private ReceiveMessageResponse response(Message... messages) {
        return ReceiveMessageResponse.builder().messages(List.of(messages)).build();
    }

    private Message message(long snapshotId) {
        String payload = codec.encode(new TestExecutionRequestedMessage(
                UUID.randomUUID(), 42, snapshotId, Instant.parse("2026-01-01T00:00:00Z")));
        return Message.builder()
                .messageId("message-" + snapshotId)
                .receiptHandle("receipt-" + snapshotId)
                .body(payload)
                .build();
    }

    private boolean awaitInFlight(WorkItemConcurrencyController controller, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (controller.currentInFlight() != expected && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        return controller.currentInFlight() == expected;
    }
}
