package com.guardbench.guardrail.infrastructure.bedrock;

import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guardbench.testrun.application.port.out.GuardrailExecutionPort;
import com.guardbench.testrun.application.port.out.GuardrailMaterializationPort;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrock.BedrockClient;
import software.amazon.awssdk.services.bedrock.BedrockClientBuilder;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClientBuilder;

/**
 * Worker 모드에서 Bedrock SDK 클라이언트와 Port 구현 어댑터를 등록한다.
 *
 * <p>{@code guardbench.worker.enabled=true}일 때만 활성화하여
 * 일반 API 모드에서 불필요한 AWS SDK 초기화를 방지한다.
 *
 * <p>Spring Cloud AWS를 사용하지 않고 AWS SDK DefaultCredentialsProvider 체인을 사용한다.
 * region은 SQS와 동일한 환경 변수(AWS_REGION)를 사용하고 endpointOverride는
 * LocalStack 등 테스트 환경에서 활용한다.
 */
@Configuration
@ConditionalOnProperty(name = "guardbench.worker.enabled", havingValue = "true")
class BedrockWorkerConfiguration {

    @Bean
    BedrockClient bedrockClient(
            @Value("${guardbench.sqs.region:ap-northeast-2}") String region,
            @Value("${guardbench.sqs.endpoint-override:}") String endpointOverride
    ) {
        BedrockClientBuilder builder = BedrockClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (endpointOverride != null && !endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride));
        }

        return builder.build();
    }

    @Bean
    BedrockRuntimeClient bedrockRuntimeClient(
            @Value("${guardbench.sqs.region:ap-northeast-2}") String region,
            @Value("${guardbench.sqs.endpoint-override:}") String endpointOverride
    ) {
        BedrockRuntimeClientBuilder builder = BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        if (endpointOverride != null && !endpointOverride.isBlank()) {
            builder.endpointOverride(URI.create(endpointOverride));
        }

        return builder.build();
    }

    @Bean
    GuardrailMaterializationPort guardrailMaterializationPort(BedrockClient bedrockClient) {
        return new BedrockGuardrailMaterializationAdapter(bedrockClient);
    }

    @Bean
    GuardrailExecutionPort guardrailExecutionPort(BedrockRuntimeClient bedrockRuntimeClient) {
        return new BedrockGuardrailExecutionAdapter(bedrockRuntimeClient);
    }
}
