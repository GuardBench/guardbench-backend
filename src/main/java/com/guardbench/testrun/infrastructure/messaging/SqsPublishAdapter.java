package com.guardbench.testrun.infrastructure.messaging;

import java.util.Map;
import java.util.Objects;

import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.port.out.SqsPublishPort;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** AWS SDK SQS 발행을 TestRun Application Port로 변환한다. */
public final class SqsPublishAdapter implements SqsPublishPort {

    private final SqsClient sqsClient;
    private final Map<TestRunQueue, String> queueUrls;

    public SqsPublishAdapter(SqsClient sqsClient, Map<TestRunQueue, String> queueUrls) {
        this.sqsClient = Objects.requireNonNull(sqsClient, "sqsClient must not be null");
        this.queueUrls = Map.copyOf(queueUrls);
    }

    @Override
    public boolean publish(TestRunQueue queue, String payload) {
        String queueUrl = queueUrls.get(queue);
        if (queueUrl == null || queueUrl.isBlank()) {
            throw new IllegalStateException("SQS URL is not configured for " + queue);
        }
        try {
            sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(payload).build());
            return true;
        } catch (SdkException exception) {
            return false;
        }
    }
}
