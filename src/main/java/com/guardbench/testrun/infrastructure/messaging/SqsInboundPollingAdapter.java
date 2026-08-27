package com.guardbench.testrun.infrastructure.messaging;

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

    // Worker 서비스들 — queue별로 하나만 사용됨
    private final ResolveTestRunService resolveService;
    private final ExecuteTestRunService executeService;
    private final HandleTestExecutionCompletedPort handleCompletedPort;

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
        this.sqsClient = Objects.requireNonNull(sqsClient);
        this.codec = Objects.requireNonNull(codec);
        this.queueUrl = Objects.requireNonNull(queueUrl);
        this.queueType = Objects.requireNonNull(queueType);
        this.pollingConfig = Objects.requireNonNull(pollingConfig);
        this.resolveService = resolveService;
        this.executeService = executeService;
        this.handleCompletedPort = handleCompletedPort;
    }

    /**
     * 한 번의 polling 사이클을 수행한다. 메시지를 수신해 디코딩 → 처리 → ack 판정한다.
     *
     * @return 처리한 메시지 수 (ack + nack 포함)
     */
    public int poll() {
        List<Message> messages = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(pollingConfig.maxMessages())
                .waitTimeSeconds(pollingConfig.waitTimeSeconds())
                .visibilityTimeout(pollingConfig.visibilityTimeoutSeconds())
                .build()).messages();

        int processedCount = 0;
        for (Message message : messages) {
            processMessage(message);
            processedCount++;
        }
        return processedCount;
    }

    private void processMessage(Message message) {
        TestRunMessage decoded;
        try {
            decoded = codec.decode(message.body());
        } catch (InvalidTestRunMessageException exception) {
            // malformed 메시지: 삭제하지 않음 → SQS maxReceiveCount 소진 후 DLQ로 이동
            log.warn("Malformed message on {}. messageId={}, error={}",
                    queueType.queueName(), message.messageId(), exception.getMessage());
            return;
        }

        Long snapshotId = snapshotId(decoded);
        String targetType = targetType(decoded);
        log.info("SQS message received. queue={} messageId={} eventId={} eventType={} testRunId={} snapshotId={} targetType={}",
                queueType.queueName(), message.messageId(), decoded.eventId(), decoded.eventType(),
                decoded.testRunId(), snapshotId, targetType);

        boolean shouldAck;
        try {
            log.info("SQS message processing started. queue={} eventId={} eventType={} testRunId={} snapshotId={} targetType={}",
                    queueType.queueName(), decoded.eventId(), decoded.eventType(), decoded.testRunId(), snapshotId, targetType);
            shouldAck = dispatch(decoded);
        } catch (Exception exception) {
            log.error("SQS message processing failed. queue={} eventId={} eventType={} testRunId={} snapshotId={} targetType={} failureType={}",
                    queueType.queueName(), decoded.eventId(), decoded.eventType(), decoded.testRunId(), snapshotId,
                    targetType, exception.getClass().getSimpleName());
            return;
        }

        if (shouldAck) {
            log.info("SQS message processing completed. queue={} eventId={} eventType={} testRunId={} snapshotId={} targetType={}",
                    queueType.queueName(), decoded.eventId(), decoded.eventType(), decoded.testRunId(), snapshotId, targetType);
            if (deleteMessage(message)) {
                log.info("SQS message deleted. queue={} eventId={} eventType={} testRunId={} snapshotId={} targetType={}",
                        queueType.queueName(), decoded.eventId(), decoded.eventType(), decoded.testRunId(), snapshotId, targetType);
            }
            return;
        }
        log.warn("SQS message processing deferred for retry. queue={} eventId={} eventType={} testRunId={} snapshotId={} targetType={}",
                queueType.queueName(), decoded.eventId(), decoded.eventType(), decoded.testRunId(), snapshotId, targetType);
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
                var outcome = executeService.execute(
                        executionRequested.snapshotId(),
                        executionRequested.targetType()
                );
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

    private static Long snapshotId(TestRunMessage message) {
        return switch (message) {
            case TestRunRequestedMessage ignored -> null;
            case TestExecutionRequestedMessage requested -> requested.snapshotId();
            case TestExecutionCompletedMessage completed -> completed.snapshotId();
        };
    }

    private static String targetType(TestRunMessage message) {
        return switch (message) {
            case TestRunRequestedMessage ignored -> null;
            case TestExecutionRequestedMessage requested -> requested.targetType().name();
            case TestExecutionCompletedMessage completed -> completed.targetType().name();
        };
    }
}
