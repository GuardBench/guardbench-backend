package com.guardbench.target.infrastructure.http;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.guardbench.testrun.application.port.out.TargetExecutionPort;
import com.guardbench.testrun.application.port.out.TargetPreparationPort;

import tools.jackson.databind.ObjectMapper;

/** Worker 모드의 HTTP Application Target adapter 조립 설정이다. */
@Configuration
@ConditionalOnProperty(name = "guardbench.worker.enabled", havingValue = "true")
@EnableConfigurationProperties(HttpEndpointProperties.class)
class HttpEndpointWorkerConfiguration {

    @Bean
    HttpClient httpEndpointClient(HttpEndpointProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean
    TargetPreparationPort targetPreparationPort(HttpEndpointTargetStore targetStore) {
        return new HttpEndpointPreparationAdapter(targetStore);
    }

    @Bean
    TargetExecutionPort targetExecutionPort(
            HttpClient httpEndpointClient,
            HttpEndpointTargetStore targetStore,
            ObjectMapper objectMapper,
            HttpEndpointProperties properties
    ) {
        return new OpenAiCompatibleExecutionAdapter(
                httpEndpointClient, targetStore, objectMapper, properties);
    }
}
