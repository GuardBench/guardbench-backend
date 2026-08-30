package com.guardbench.target.infrastructure.bedrock;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guardbench.testrun.application.port.out.TargetExecutionPort;
import com.guardbench.testrun.application.port.out.TargetPreparationPort;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.api.RetryStrategy;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/**
 * Worker 모드에서 Bedrock SDK 클라이언트와 Port 구현 어댑터를 등록한다.
 *
 * <p>{@code guardbench.worker.enabled=true}일 때만 활성화하여
 * 일반 API 모드에서 불필요한 AWS SDK 초기화를 방지한다.
 *
 * <p>Spring Cloud AWS를 사용하지 않고 AWS SDK DefaultCredentialsProvider 체인을 사용한다.
 *
 * <p>ADR 0005: SDK 재시도까지 포함한 전체 호출 한도를 15초로 적용한다.
 * 개별 시도 한도와 최대 시도 횟수를 함께 제한해 execution claim lease(45초)를 넘지 않게 한다.
 * 한도를 초과한 호출은 SDK가 timeout 예외로 종료하고 Worker가 이를 TIMED_OUT으로 저장한다.
 */
@Configuration
@ConditionalOnProperty(name = "guardbench.worker.enabled", havingValue = "true")
@EnableConfigurationProperties(BedrockProperties.class)
class BedrockWorkerConfiguration {

    @Bean
    BedrockClient bedrockClient(BedrockProperties properties) {
        return configure(BedrockClient.builder(), properties).build();
    }

    @Bean
    BedrockRuntimeClient bedrockRuntimeClient(BedrockProperties properties) {
        return configure(BedrockRuntimeClient.builder(), properties).build();
    }

    @Bean
    TargetPreparationPort targetPreparationPort(
            BedrockClient bedrockClient,
            BedrockGuardrailTargetStore targetStore
    ) {
        return new BedrockGuardrailPreparationAdapter(bedrockClient, targetStore);
    }

    @Bean
    TargetExecutionPort targetExecutionPort(
            BedrockRuntimeClient bedrockRuntimeClient,
            BedrockGuardrailTargetStore targetStore
    ) {
        return new BedrockGuardrailExecutionAdapter(bedrockRuntimeClient, targetStore);
    }

    private static <B extends AwsClientBuilder<B, ?>> B configure(B builder, BedrockProperties properties) {
        builder.region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(overrideConfiguration(properties));

        if (properties.endpointOverride() != null && !properties.endpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpointOverride()));
        }

        return builder;
    }

    /**
     * ADR 0005의 Provider 호출 한도를 SDK 설정으로 표현한다.
     */
    static ClientOverrideConfiguration overrideConfiguration(BedrockProperties properties) {
        RetryStrategy retryStrategy = AwsRetryStrategy.standardRetryStrategy()
                .toBuilder()
                .maxAttempts(properties.maxAttempts())
                .build();

        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofMillis(properties.apiCallTimeoutMs()))
                .apiCallAttemptTimeout(Duration.ofMillis(properties.apiCallAttemptTimeoutMs()))
                .retryStrategy(retryStrategy)
                .build();
    }
}
