package com.guardbench.testrun.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.port.out.PublishBatchResult;
import com.guardbench.testrun.application.port.out.SqsPublishPort.PublishBatchEntry;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/**
 * SqsPublishAdapter의 SendMessageBatch 발행 통합 테스트다.
 *
 * <p>ADR 0005: batch 안 한 항목의 발행 실패가 나머지 항목 처리를 막지 않아야 한다.
 * SQS SendMessageBatch는 메시지 본문이 256KB를 넘는 항목만 batch API 수준에서
 * 개별 실패로 분류하므로, 정상 항목과 과대 payload 항목을 같은 batch에 섞어
 * 부분 실패를 실제 SQS 프로토콜로 재현한다.
 */
class SqsPublishAdapterIntegrationTest {

    private static final LocalStackContainer LOCAL_STACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.SQS);

    private static SqsClient sqs;
    private static String queueUrl;

    @BeforeAll
    static void setup() {
        LOCAL_STACK.start();
        sqs = SqsClient.builder()
                .endpointOverride(URI.create(LOCAL_STACK.getEndpoint().toString()))
                .region(Region.of(LOCAL_STACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCAL_STACK.getAccessKey(), LOCAL_STACK.getSecretKey())))
                .build();
        queueUrl = sqs.createQueue(CreateQueueRequest.builder()
                .queueName("sqs-publish-adapter-test").build()).queueUrl();
    }

    @AfterAll
    static void teardown() {
        if (sqs != null) {
            sqs.close();
        }
        LOCAL_STACK.stop();
    }

    @BeforeEach
    void drainQueue() {
        while (true) {
            List<Message> batch = sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl).maxNumberOfMessages(10).waitTimeSeconds(1).build()).messages();
            if (batch.isEmpty()) {
                return;
            }
            for (Message message : batch) {
                sqs.deleteMessage(r -> r.queueUrl(queueUrl).receiptHandle(message.receiptHandle()));
            }
        }
    }

    @Test
    @DisplayName("정상 batch는 SendMessageBatch로 전송되고 모든 eventId가 성공으로 반환된다")
    void publishesWholeBatchSuccessfully() {
        SqsPublishAdapter adapter = new SqsPublishAdapter(sqs, Map.of(TestRunQueue.RESOLVE, queueUrl));

        PublishBatchEntry first = new PublishBatchEntry(UUID.randomUUID(), samplePayload("1"));
        PublishBatchEntry second = new PublishBatchEntry(UUID.randomUUID(), samplePayload("2"));

        PublishBatchResult result = adapter.publishBatch(TestRunQueue.RESOLVE, List.of(first, second));

        assertThat(result.succeeded(first.eventId())).isTrue();
        assertThat(result.succeeded(second.eventId())).isTrue();

        List<Message> delivered = receiveAll();
        assertThat(delivered).hasSize(2);
    }

    @Test
    @DisplayName("batch 안 한 항목이 허용되지 않는 문자를 포함하면 그 항목만 실패로 반환되고 나머지는 발행된다")
    void invalidEntryFailsWithoutBlockingOthers() {
        SqsPublishAdapter adapter = new SqsPublishAdapter(sqs, Map.of(TestRunQueue.RESOLVE, queueUrl));

        PublishBatchEntry normal = new PublishBatchEntry(UUID.randomUUID(), samplePayload("1"));
        // SQS는 U+0000-U+0008, U+000B, U+000C, U+000E-U+001F 등 제어 문자를 포함한
        // 메시지 본문을 InvalidMessageContents로 개별 거부한다(batch 전체가 아니다).
        PublishBatchEntry invalid = new PublishBatchEntry(UUID.randomUUID(), samplePayload("2") + "\u0000");

        PublishBatchResult result = adapter.publishBatch(TestRunQueue.RESOLVE, List.of(normal, invalid));

        assertThat(result.succeeded(normal.eventId()))
                .as("정상 항목은 같은 batch의 다른 항목 실패와 무관하게 발행돼야 한다")
                .isTrue();
        assertThat(result.succeeded(invalid.eventId()))
                .as("허용되지 않는 문자를 포함한 항목은 실패로 반환돼야 한다")
                .isFalse();

        List<Message> delivered = receiveAll();
        assertThat(delivered).hasSize(1);
        assertThat(delivered.getFirst().body()).contains("\"testRunId\":1");
    }

    private static List<Message> receiveAll() {
        List<Message> collected = new java.util.ArrayList<>();
        for (int attempt = 0; attempt < 5; attempt++) {
            List<Message> batch = sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl).maxNumberOfMessages(10).waitTimeSeconds(1).build()).messages();
            collected.addAll(batch);
            if (batch.isEmpty()) {
                break;
            }
        }
        return collected;
    }

    private static String samplePayload(String testRunId) {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"TestRunRequested\",\"schemaVersion\":1,\"testRunId\":"
                + testRunId + ",\"occurredAt\":\"2026-08-26T00:00:00Z\"}";
    }
}
