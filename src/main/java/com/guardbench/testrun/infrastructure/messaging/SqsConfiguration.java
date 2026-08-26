package com.guardbench.testrun.infrastructure.messaging;

import java.net.URI;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guardbench.testrun.application.messaging.TestRunQueue;
import com.guardbench.testrun.application.port.out.SqsPublishPort;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

/**
 * AWS SDK SqsClient와 SQS 어댑터 빈 설정이다.
 *
 * <p>ADR 0005에 따라 Spring Cloud AWS를 사용하지 않고
 * AWS SDK 직접 구성 + DefaultCredentialsProvider 체인을 사용한다.
 *
 * <p>guardbench.sqs.enabled=true일 때만 활성화한다.
 * 통합 테스트에서 SQS가 불필요하면 이 속성을 비우거나 생략한다.
 */
@Configuration
@ConditionalOnProperty(name = "guardbench.sqs.enabled", havingValue = "true")
@EnableConfigurationProperties(SqsProperties.class)
class SqsConfiguration {

    @Bean
    SqsClient sqsClient(SqsProperties properties) {
        SqsClientBuilder builder = SqsClient.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (properties.endpointOverride() != null && !properties.endpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpointOverride()));
        }

        return builder.build();
    }

    @Bean
    SqsPublishPort sqsPublishPort(SqsClient sqsClient, SqsProperties properties) {
        Map<TestRunQueue, String> queueUrls = resolveQueueUrls(sqsClient, properties);
        return new SqsPublishAdapter(sqsClient, queueUrls);
    }

    private Map<TestRunQueue, String> resolveQueueUrls(SqsClient sqsClient, SqsProperties properties) {
        Map<TestRunQueue, String> urls = new EnumMap<>(TestRunQueue.class);

        SqsProperties.QueueUrls configured = properties.queueUrls();
        urls.put(TestRunQueue.RESOLVE, resolveUrl(sqsClient, configured != null ? configured.resolve() : null, TestRunQueue.RESOLVE));
        urls.put(TestRunQueue.WORK_ITEMS, resolveUrl(sqsClient, configured != null ? configured.workItems() : null, TestRunQueue.WORK_ITEMS));
        urls.put(TestRunQueue.FINALIZE, resolveUrl(sqsClient, configured != null ? configured.runFinalize() : null, TestRunQueue.FINALIZE));

        return urls;
    }

    private String resolveUrl(SqsClient sqsClient, String explicitUrl, TestRunQueue queue) {
        if (explicitUrl != null && !explicitUrl.isBlank()) {
            return explicitUrl;
        }
        return sqsClient.getQueueUrl(r -> r.queueName(queue.queueName())).queueUrl();
    }
}
