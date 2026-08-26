package com.guardbench.testrun.infrastructure.messaging;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;

import com.guardbench.testrun.application.messaging.TestRunQueue;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

class SqsQueueTopologyIntegrationTest {

    private static final LocalStackContainer LOCAL_STACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.0"))
            .withServices(LocalStackContainer.Service.SQS);

    private static SqsClient sqs;

    @BeforeAll
    static void openClient() {
        LOCAL_STACK.start();
        sqs = SqsClient.builder()
                .endpointOverride(URI.create(LOCAL_STACK.getEndpoint().toString()))
                .region(Region.of(LOCAL_STACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(LOCAL_STACK.getAccessKey(), LOCAL_STACK.getSecretKey())))
                .build();
    }

    @AfterAll
    static void closeClient() {
        if (sqs != null) {
            sqs.close();
        }
        LOCAL_STACK.stop();
    }

    @Test
    @DisplayName("세 Worker queue는 각각 DLQ와 maxReceiveCount=5 redrive policy로 생성할 수 있다")
    void createsWorkerQueuesWithApprovedRedrivePolicy() {
        for (TestRunQueue queue : TestRunQueue.values()) {
            String deadLetterQueueUrl = sqs.createQueue(CreateQueueRequest.builder()
                    .queueName(queue.deadLetterQueueName())
                    .build()).queueUrl();
            String deadLetterQueueArn = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(deadLetterQueueUrl)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)
                    .build()).attributes().get(QueueAttributeName.QUEUE_ARN);

            String queueUrl = sqs.createQueue(CreateQueueRequest.builder()
                    .queueName(queue.queueName())
                    .attributes(Map.of(
                            QueueAttributeName.VISIBILITY_TIMEOUT, "30",
                            QueueAttributeName.REDRIVE_POLICY,
                            "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"5\"}".formatted(deadLetterQueueArn)))
                    .build()).queueUrl();

            String redrivePolicy = sqs.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(QueueAttributeName.REDRIVE_POLICY)
                    .build()).attributes().get(QueueAttributeName.REDRIVE_POLICY);
            assertTrue(redrivePolicy.contains(deadLetterQueueArn));
            assertTrue(redrivePolicy.contains("5"));
        }
    }
}
