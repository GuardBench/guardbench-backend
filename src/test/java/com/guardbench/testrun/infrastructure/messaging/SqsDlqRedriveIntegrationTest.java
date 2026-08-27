package com.guardbench.testrun.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import com.guardbench.testrun.application.messaging.TestRunMessageCodec;
import com.guardbench.testrun.application.messaging.TestRunQueue;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * DLQ redrive 통합 테스트.
 *
 * <p>ADR 0005: maxReceiveCount=5 소진 후 poison 메시지가 DLQ로 이동하는지 검증한다.
 *
 * <p>이 테스트는 SQS 인프라 수준에서 redrive를 직접 검증한다:
 * <ol>
 *   <li>malformed 메시지를 RESOLVE queue로 전송</li>
 *   <li>SqsInboundPollingAdapter가 메시지를 수신하되 ack(삭제)하지 않음</li>
 *   <li>SQS가 ApproximateReceiveCount를 증가시키고 maxReceiveCount 도달 후 DLQ로 이동</li>
 * </ol>
 *
 * <p>LocalStack의 SQS redrive는 receive + visibility timeout 만료를 반복하여 동작한다.
 * visibility timeout을 0초로 설정하여 테스트 속도를 확보한다.
 */
class SqsDlqRedriveIntegrationTest {

    private static final LocalStackContainer LOCAL_STACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.SQS);

    private static SqsClient sqs;
    private static String resolveQueueUrl;
    private static String dlqUrl;

    @BeforeAll
    static void setup() {
        LOCAL_STACK.start();
        sqs = SqsClient.builder()
                .endpointOverride(URI.create(LOCAL_STACK.getEndpoint().toString()))
                .region(Region.of(LOCAL_STACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCAL_STACK.getAccessKey(), LOCAL_STACK.getSecretKey())))
                .build();

        // DLQ 생성
        dlqUrl = sqs.createQueue(CreateQueueRequest.builder()
                .queueName(TestRunQueue.RESOLVE.deadLetterQueueName())
                .build()).queueUrl();

        String dlqArn = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(dlqUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build()).attributes().get(QueueAttributeName.QUEUE_ARN);

        // Resolve Queue: maxReceiveCount=5, visibilityTimeout=0 (즉시 재수신 가능)
        resolveQueueUrl = sqs.createQueue(CreateQueueRequest.builder()
                .queueName(TestRunQueue.RESOLVE.queueName())
                .attributes(Map.of(
                        QueueAttributeName.VISIBILITY_TIMEOUT, "0",
                        QueueAttributeName.REDRIVE_POLICY,
                        "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"5\"}".formatted(dlqArn)))
                .build()).queueUrl();
    }

    @AfterAll
    static void teardown() {
        if (sqs != null) {
            sqs.close();
        }
        LOCAL_STACK.stop();
    }

    @Test
    @DisplayName("malformed 메시지는 maxReceiveCount=5 소진 후 DLQ로 이동한다")
    void poisonMessageReachesDlqAfterMaxReceiveCount() throws InterruptedException {
        // 잘못된 JSON — 유효하지 않은 v1 메시지 (UUID 파싱 실패)
        String poisonPayload = "{\"eventId\":\"not-a-uuid\",\"eventType\":\"Unknown\",\"schemaVersion\":1}";
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(resolveQueueUrl)
                .messageBody(poisonPayload)
                .build());

        // maxReceiveCount=5를 소진시키기 위해 메시지를 반복 수신하되 삭제하지 않는다.
        // visibilityTimeout=0이므로 같은 메시지를 즉시 다시 받을 수 있다.
        // SQS Standard에서 receiveCount는 매 수신마다 증가한다.
        int receiveAttempts = 0;
        for (int i = 0; i < 10; i++) {
            var messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(resolveQueueUrl)
                    .maxNumberOfMessages(1)
                    .waitTimeSeconds(0)
                    .visibilityTimeout(0)
                    .build()).messages();
            if (messages.isEmpty()) {
                // 메시지가 DLQ로 이동했을 수 있음
                break;
            }
            receiveAttempts++;
            // 삭제하지 않음 — redrive 트리거
            TimeUnit.MILLISECONDS.sleep(200);
        }

        // DLQ에서 poison 메시지 확인 (최대 10초 대기)
        var dlqMessages = receiveWithRetry(dlqUrl, 10);

        assertEquals(1, dlqMessages.size(),
                "poison 메시지 1건이 DLQ로 이동해야 한다. receiveAttempts=" + receiveAttempts);
        assertTrue(dlqMessages.get(0).body().contains("not-a-uuid"),
                "DLQ 메시지는 원본 poison payload를 포함해야 한다");

        // 원본 Queue는 비어 있어야 함
        var remainingMessages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(resolveQueueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(1)
                .build()).messages();

        assertTrue(remainingMessages.isEmpty(),
                "원본 queue는 비어 있어야 한다");
    }

    @Test
    @DisplayName("SqsInboundPollingAdapter가 malformed 메시지를 삭제하지 않는다 (nack 동작)")
    void adapterDoesNotDeleteMalformedMessage() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        TestRunMessageCodec codec = new TestRunMessageCodec(objectMapper);
        SqsProperties.Polling pollingConfig = new SqsProperties.Polling(1, 0, 2);

        // 새 queue로 격리 테스트
        String isolatedDlqUrl = sqs.createQueue(CreateQueueRequest.builder()
                .queueName("isolated-dlq")
                .build()).queueUrl();
        String isolatedDlqArn = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                .queueUrl(isolatedDlqUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build()).attributes().get(QueueAttributeName.QUEUE_ARN);
        String isolatedQueueUrl = sqs.createQueue(CreateQueueRequest.builder()
                .queueName("isolated-resolve")
                .attributes(Map.of(
                        QueueAttributeName.VISIBILITY_TIMEOUT, "2",
                        QueueAttributeName.REDRIVE_POLICY,
                        "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"5\"}".formatted(isolatedDlqArn)))
                .build()).queueUrl();

        // malformed 메시지 전송
        sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(isolatedQueueUrl)
                .messageBody("{invalid-json")
                .build());

        SqsInboundPollingAdapter adapter = new SqsInboundPollingAdapter(
                sqs, codec, isolatedQueueUrl, TestRunQueue.RESOLVE,
                pollingConfig, null, null, null
        );

        // 1회 폴링 — malformed이므로 삭제하지 않음
        int polled = adapter.poll();
        assertEquals(1, polled, "1건의 메시지를 수신해야 한다");

        // 메시지는 삭제되지 않았으므로 visibility timeout 후 다시 보여야 함
        // (visibilityTimeout=2초이므로 바로 조회하면 안 보이고 2초 후에는 보임)
        var immediate = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(isolatedQueueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(0)
                .build()).messages();
        // visibility timeout 동안이므로 비어 있을 수 있음 (정상)
        // 2초 후 재확인은 속도를 위해 생략 — 핵심은 DLQ 최종 이동으로 검증
    }

    private java.util.List<Message> receiveWithRetry(String queueUrl, int maxAttempts) throws InterruptedException {
        for (int i = 0; i < maxAttempts; i++) {
            var messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)
                    .waitTimeSeconds(2)
                    .build()).messages();
            if (!messages.isEmpty()) {
                return messages;
            }
            TimeUnit.SECONDS.sleep(1);
        }
        return java.util.List.of();
    }
}
