package com.guardbench.testrun.infrastructure.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.guardbench.testrun.application.ExecuteTestRunService;
import com.guardbench.testrun.application.ResolveTestRunService;
import com.guardbench.testrun.application.messaging.InvalidTestRunMessageException;
import com.guardbench.testrun.application.messaging.TestExecutionCompletedMessage;
import com.guardbench.testrun.application.messaging.TestExecutionRequestedMessage;
import com.guardbench.testrun.application.messaging.TestRunMessage;
import com.guardbench.testrun.application.messaging.TestRunMessageCodec;
import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.messaging.TestRunRequestedMessage;
import com.guardbench.testrun.application.port.in.HandleTestExecutionCompletedPort;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * SQS 메시지를 폴링하여 Application Service에 전달하고,
 * ADR 0005 ack/nack 규칙에 따라 메시지를 삭제하거나 재전달을 허용한다.
 *
 * <p>InvalidTestRunMessageException(malformed)은 삭제하지 않고 SQS redrive policy에 맡긴다.
 * <p>Application Service의 결과가 shouldAcknowledge()이면 삭제하고, 아니면 visibility timeout 후 재전달된다.
 *
 * <p>이 어댑터는 {@code @Scheduled} 또는 별도 쓰레드에서 {@link #poll()}을 호출해야 한다.
 */
public class SqsInboundPollingAdapter {

    private static final Logger log = LoggerFactory.getLogger(SqsInboundPollingAdapter.class);

    private final SqsClient sqsClient;
    private final TestRunMessageCodec codec;
    private final String queueUrl;
    private final TestRunQueue queueType;
    private final SqsProperties.Polling pollingConfig;
    private final WorkItemConcurrencyController workItemConcurrencyController;

    // Worker 서비스들 — queue별로 하나만 사용됨
    private final ResolveTestRunService resolveService;
    private final ExecuteTestRunService executeService;
    private final HandleTestExecutionCompletedPort handleCompletedPort;

    SqsInboundPollingAdapter(
            SqsClient sqsClient,
            TestRunMessageCodec codec,
            String queueUrl,
            TestRunQueue queueType,
            SqsProperties.Polling pollingConfig,
            ResolveTestRunService resolveService,
            ExecuteTestRunService executeService,
            HandleTestExecutionCompletedPort handleCompletedPort,
            WorkItemConcurrencyController workItemConcurrencyController
    ) {
        this.sqsClient = Objects.requireNonNull(sqsClient);
        this.codec = Objects.requireNonNull(codec);
        this.queueUrl = Objects.requireNonNull(queueUrl);
        this.queueType = Objects.requireNonNull(queueType);
        this.pollingConfig = Objects.requireNonNull(pollingConfig);
        this.workItemConcurrencyController = workItemConcurrencyController;
        this.resolveService = resolveService;
        this.executeService = executeService;
        this.handleCompletedPort = handleCompletedPort;
    }

    public SqsInboundPollingAdapter(
            SqsClient sqsClient,
            TestRunMessageCodec codec,
            String queueUrl,
            TestRunQueue queueType,
            SqsProperties.Polling pollingConfig,
            ResolveTestRunService resolveService,
            ExecuteTestRunService executeService,
            HandleTestExecutionCompletedPort handleCompletedPort
    ) {
        this(sqsClient, codec, queueUrl, queueType, pollingConfig, resolveService, executeService,
                handleCompletedPort, null);
    }

    /**
     * 한 번의 polling 사이클을 수행한다. 메시지를 수신해 디코딩 → 처리 → ack 판정한다.
     *
     * @return 처리한 메시지 수 (ack + nack 포함)
     */
    public int poll() {
        if (queueType == TestRunQueue.WORK_ITEMS && workItemConcurrencyController != null) {
            return pollWorkItems();
        }

        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(pollingConfig.maxMessages())
                .waitTimeSeconds(pollingConfig.waitTimeSeconds())
                .visibilityTimeout(pollingConfig.visibilityTimeoutSeconds())
                .messageSystemAttributeNames(MessageSystemAttributeName.SENT_TIMESTAMP)
                .build()).messages();

        // Capture receipt once: processing earlier messages must not inflate queue wait.
        Instant receivedAt = Instant.now();
        int processedCount = 0;
        for (Message message : messages) {
            processMessage(message, receivedAt);
            processedCount++;
        }
        return processedCount;
    }

    private int pollWorkItems() {
        int requestedMessages = workItemConcurrencyController.reserveAvailableSlots(
                Math.min(pollingConfig.maxMessages(), 10));
        if (requestedMessages == 0) {
            log.debug("WorkItems polling을 건너뜁니다. configuredConcurrency={} currentInFlight={} availableSlots=0",
                    workItemConcurrencyController.concurrency(),
                    workItemConcurrencyController.currentInFlight());
            return 0;
        }

        List<Message> messages;
        try {
            messages = receiveMessages(requestedMessages);
        } catch (RuntimeException exception) {
            workItemConcurrencyController.releaseSlots(requestedMessages);
            throw exception;
        }

        if (messages.size() > requestedMessages) {
            workItemConcurrencyController.releaseSlots(requestedMessages);
            throw new IllegalStateException("SQS returned more WorkItems than reserved slots");
        }
        workItemConcurrencyController.releaseSlots(requestedMessages - messages.size());
        Instant receivedAt = Instant.now();
        for (Message message : messages) {
            boolean submitted = workItemConcurrencyController.submit(() -> processMessage(message, receivedAt));
            if (!submitted) {
                log.warn("WorkItem 처리를 제출하지 못해 메시지를 재전달에 맡깁니다. queue={} messageId={} "
                                + "currentInFlight={}",
                        queueType.queueName(), message.messageId(),
                        workItemConcurrencyController.currentInFlight());
            }
        }
        return messages.size();
    }

    private List<Message> receiveMessages(int maxNumberOfMessages) {
        return sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxNumberOfMessages)
                .waitTimeSeconds(pollingConfig.waitTimeSeconds())
                .visibilityTimeout(pollingConfig.visibilityTimeoutSeconds())
                .messageSystemAttributeNames(MessageSystemAttributeName.SENT_TIMESTAMP)
                .build()).messages();
    }

    private void processMessage(Message message, Instant receivedAt) {
        TestRunMessage decoded;
        try {
            decoded = codec.decode(message.body());
        } catch (InvalidTestRunMessageException exception) {
            // malformed 메시지: 삭제하지 않음 → SQS maxReceiveCount 소진 후 DLQ로 이동
            log.warn("{}에서 잘못된 형식의 메시지를 수신했습니다. messageId={}, error={}",
                    queueType.queueName(), message.messageId(), exception.getMessage());
            return;
        }

        Long snapshotId = snapshotId(decoded);
        log.info("SQS 메시지를 수신했습니다. queue={} messageId={} eventId={} eventType={} testRunId={} snapshotId={}",
                queueType.queueName(), message.messageId(), decoded.eventId(), decoded.eventType(),
                decoded.testRunId(), snapshotId);

        if (decoded instanceof TestExecutionRequestedMessage) {
            Long sentTimestamp = sentTimestamp(message);
            Long queueWaitMs = sentTimestamp != null && sentTimestamp <= receivedAt.toEpochMilli()
                    ? receivedAt.toEpochMilli() - sentTimestamp : null;
            log.info("WorkItem 수신 timing을 기록합니다. testRunId={} snapshotId={} eventId={} messageId={} "
                            + "receivedAt={} sentTimestamp={} queueWaitMs={}",
                    decoded.testRunId(), snapshotId, decoded.eventId(), message.messageId(),
                    receivedAt, sentTimestamp, queueWaitMs);
        }

        boolean shouldAck;
        try {
            Long workerDispatchWaitMs = decoded instanceof TestExecutionRequestedMessage
                    ? Math.max(0, Instant.now().toEpochMilli() - receivedAt.toEpochMilli())
                    : null;
            Integer configuredConcurrency = workItemConcurrencyController == null
                    ? null : workItemConcurrencyController.concurrency();
            Integer currentInFlight = workItemConcurrencyController == null
                    ? null : workItemConcurrencyController.currentInFlight();
            log.info("SQS 메시지 처리를 시작합니다. queue={} eventId={} eventType={} testRunId={} snapshotId={} "
                            + "workerDispatchWaitMs={} configuredConcurrency={} currentInFlight={}",
                    queueType.queueName(), decoded.eventId(), decoded.eventType(), decoded.testRunId(), snapshotId,
                    workerDispatchWaitMs, configuredConcurrency, currentInFlight);
            shouldAck = dispatch(decoded);
        } catch (Exception exception) {
            log.error("SQS 메시지 처리에 실패했습니다. queue={} eventId={} eventType={} testRunId={} snapshotId={} failureType={}",
                    queueType.queueName(), decoded.eventId(), decoded.eventType(), decoded.testRunId(), snapshotId,
                    exception.getClass().getSimpleName());
            return;
        }

        if (shouldAck) {
            if (workItemConcurrencyController != null && !workItemConcurrencyController.canAcknowledge()) {
                log.warn("WorkItem이 종료 timeout 이후 완료되어 ACK하지 않습니다. queue={} eventId={} "
                                + "testRunId={} snapshotId={}",
                        queueType.queueName(), decoded.eventId(), decoded.testRunId(), snapshotId);
                return;
            }
            log.info("SQS 메시지 처리를 완료했습니다. queue={} eventId={} eventType={} testRunId={} snapshotId={}",
                    queueType.queueName(), decoded.eventId(), decoded.eventType(), decoded.testRunId(), snapshotId);
            if (deleteMessage(message)) {
                log.info("SQS 메시지를 삭제했습니다. queue={} eventId={} eventType={} testRunId={} snapshotId={}",
                        queueType.queueName(), decoded.eventId(), decoded.eventType(), decoded.testRunId(), snapshotId);
            }
            return;
        }
        log.warn("SQS 메시지 처리를 재시도로 보류했습니다. queue={} eventId={} eventType={} testRunId={} snapshotId={}",
                queueType.queueName(), decoded.eventId(), decoded.eventType(), decoded.testRunId(), snapshotId);
        // nack: 삭제하지 않아 visibility timeout 후 재전달됨
    }

    private boolean dispatch(TestRunMessage decoded) {
        return switch (decoded) {
            case TestRunRequestedMessage requested -> {
                if (resolveService == null) {
                    log.error("ResolveTestRunService not available for queue {}", queueType);
                    yield false;
                }
                var outcome = resolveService.resolve(requested.testRunId());
                yield shouldAcknowledgeResolve(outcome);
            }
            case TestExecutionRequestedMessage executionRequested -> {
                if (executeService == null) {
                    log.error("ExecuteTestRunService not available for queue {}", queueType);
                    yield false;
                }
                var outcome = executeService.execute(executionRequested.snapshotId());
                yield outcome.shouldAcknowledge();
            }
            case TestExecutionCompletedMessage completed -> {
                if (handleCompletedPort == null) {
                    log.error("HandleTestExecutionCompletedPort not available for queue {}", queueType);
                    yield false;
                }
                yield handleCompletedPort.handle(completed.testRunId());
            }
        };
    }

    /**
     * ADR 0005 ack 규칙: resolve outcome → ack 여부
     */
    private static boolean shouldAcknowledgeResolve(ResolveTestRunService.ResolutionOutcome outcome) {
        return switch (outcome) {
            case RESOLVED, ALREADY_RESOLVED, NOT_FOUND, MATERIALIZATION_FAILED_TERMINAL -> true;
            case CLAIM_HELD_BY_OTHER, CLAIM_LOST_AFTER_MATERIALIZATION, MATERIALIZATION_FAILED_RETRYABLE -> false;
        };
    }

    private boolean deleteMessage(Message message) {
        try {
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
            return true;
        } catch (Exception exception) {
            // ADR 0005: SQS 삭제 실패 시 재전달 후 멱등 처리됨
            log.warn("SQS message delete failed. queue={} messageId={} failureType={}",
                    queueType.queueName(), message.messageId(), exception.getClass().getSimpleName());
            return false;
        }
    }

    private static Long sentTimestamp(Message message) {
        String value = message.attributes().get(MessageSystemAttributeName.SENT_TIMESTAMP);
        if (value == null) return null;
        try {
            long timestamp = Long.parseLong(value);
            return timestamp >= 0 ? timestamp : null;
        } catch (NumberFormatException exception) {
            // Optional diagnostic metadata must never change delivery/ack behavior.
            return null;
        }
    }

    private static Long snapshotId(TestRunMessage message) {
        return switch (message) {
            case TestRunRequestedMessage ignored -> null;
            case TestExecutionRequestedMessage requested -> requested.snapshotId();
            case TestExecutionCompletedMessage completed -> completed.snapshotId();
        };
    }

}
