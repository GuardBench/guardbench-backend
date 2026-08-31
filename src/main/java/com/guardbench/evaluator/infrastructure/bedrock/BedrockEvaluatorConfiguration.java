package com.guardbench.evaluator.infrastructure.bedrock;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guardbench.testrun.application.port.out.EvaluatorExecutionPort;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.api.RetryStrategy;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

/** Worker 모드에서 Bedrock Guardrail Evaluator adapter를 조립한다. */
@Configuration
@ConditionalOnProperty(name = "guardbench.worker.enabled", havingValue = "true")
@EnableConfigurationProperties(BedrockProperties.class)
class BedrockEvaluatorConfiguration {

    @Bean
    BedrockRuntimeClient bedrockRuntimeClient(BedrockProperties properties) {
        return configure(BedrockRuntimeClient.builder(), properties).build();
    }

    @Bean
    EvaluatorExecutionPort evaluatorExecutionPort(
            BedrockRuntimeClient bedrockRuntimeClient,
            BedrockGuardrailEvaluatorStore evaluatorStore
    ) {
        return new BedrockGuardrailEvaluatorAdapter(bedrockRuntimeClient, evaluatorStore);
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
