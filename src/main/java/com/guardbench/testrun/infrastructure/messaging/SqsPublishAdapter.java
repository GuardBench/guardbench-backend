package com.guardbench.testrun.infrastructure.messaging;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.port.out.PublishBatchResult;
import com.guardbench.testrun.application.port.out.SqsPublishPort;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse;

/**
 * AWS SDK SQS {@code SendMessageBatch} 발행을 TestRun Application Port로 변환한다.
 *
 * <p>ADR 0005: batch의 한 항목 발행 실패가 나머지 항목 처리를 막지 않도록
 * SQS 응답의 {@code Successful}/{@code Failed} 항목을 그대로 결과에 매핑한다.
 * 요청 자체가 실패(SdkException)하면 batch 전체를 실패로 간주한다.
 */
public final class SqsPublishAdapter implements SqsPublishPort {

    private static final Logger log = LoggerFactory.getLogger(SqsPublishAdapter.class);
    private final SqsClient sqsClient;
    private final Map<TestRunQueue, String> queueUrls;

    public SqsPublishAdapter(SqsClient sqsClient, Map<TestRunQueue, String> queueUrls) {
        this.sqsClient = Objects.requireNonNull(sqsClient, "sqsClient must not be null");
        this.queueUrls = Map.copyOf(queueUrls);
    }

    @Override
    public PublishBatchResult publishBatch(TestRunQueue queue, List<PublishBatchEntry> entries) {
        if (entries.isEmpty()) {
            return new PublishBatchResult(Set.of());
        }

        String queueUrl = queueUrls.get(queue);
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException("SQS URL is not configured for " + queue);
        }

        List<SendMessageBatchRequestEntry> requestEntries = entries.stream()
                .map(entry -> SendMessageBatchRequestEntry.builder()
                        .id(entry.eventId().toString())
                        .messageBody(entry.payload())
                        .build())
                .collect(Collectors.toList());

        try {
            SendMessageBatchResponse response = sqsClient.sendMessageBatch(SendMessageBatchRequest.builder()
                    .queueUrl(queueUrl)
                    .entries(requestEntries)
                    .build());

            Set<UUID> succeeded = response.successful().stream()
                    .map(entry -> UUID.fromString(entry.id()))
                    .collect(Collectors.toCollection(HashSet::new));
            log.info("SQS batch publish completed. queue={} requestedCount={} succeededCount={} failedCount={}",
                    queue.queueName(), entries.size(), succeeded.size(), entries.size() - succeeded.size());
            return new PublishBatchResult(succeeded);
        } catch (SdkException exception) {
            // 요청 자체가 실패하면 batch 전체를 PENDING으로 남긴다.
            log.error("SQS batch publish failed. queue={} requestedCount={} failureType={}",
                    queue.queueName(), entries.size(), exception.getClass().getSimpleName());
            return new PublishBatchResult(Set.of());
        }
    }
}
