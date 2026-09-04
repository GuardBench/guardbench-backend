package com.guardbench.evaluator.infrastructure.sagemaker;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guardbench.testrun.application.port.out.EvaluatorExecutionPort;

import tools.jackson.databind.ObjectMapper;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.retries.api.RetryStrategy;
import software.amazon.awssdk.services.sagemakerruntime.SageMakerRuntimeClient;

/** Worker 모드에서 SageMaker Response Behavior Classifier adapter를 조립한다. */
@Configuration
@ConditionalOnProperty(name = "guardbench.worker.enabled", havingValue = "true")
@EnableConfigurationProperties(SageMakerProperties.class)
class SageMakerEvaluatorConfiguration {

    @Bean
    SageMakerRuntimeClient sageMakerRuntimeClient(SageMakerProperties properties) {
        return configure(SageMakerRuntimeClient.builder(), properties).build();
    }

    @Bean
    EvaluatorExecutionPort evaluatorExecutionPort(
            SageMakerRuntimeClient sageMakerRuntimeClient,
            SageMakerClassifierEvaluatorStore evaluatorStore,
            SageMakerClassifierProperties classifierProperties,
            ObjectMapper objectMapper
    ) {
        return new SageMakerClassifierEvaluatorAdapter(
                sageMakerRuntimeClient, evaluatorStore, classifierProperties, objectMapper);
    }

    private static <B extends AwsClientBuilder<B, ?>> B configure(B builder, SageMakerProperties properties) {
        builder.region(Region.of(properties.region()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .overrideConfiguration(overrideConfiguration(properties));

        if (properties.endpointOverride() != null && !properties.endpointOverride().isBlank()) {
            builder.endpointOverride(URI.create(properties.endpointOverride()));
        }

        return builder;
    }

    static ClientOverrideConfiguration overrideConfiguration(SageMakerProperties properties) {
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
