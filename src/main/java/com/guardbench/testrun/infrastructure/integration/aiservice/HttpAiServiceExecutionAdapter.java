package com.guardbench.testrun.infrastructure.integration.aiservice;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.guardbench.testrun.application.port.out.AiServiceExecutionPort;
import com.guardbench.testrun.application.port.out.AiServiceExecutionRequest;
import com.guardbench.testrun.application.port.out.AiServiceExecutionResult;

/**
 * 고객 AI 서비스의 최소 정상 HTTP 응답 계약을 구현한 Adapter다.
 */
@Component
public final class HttpAiServiceExecutionAdapter implements AiServiceExecutionPort {

    private final RestClient restClient;

    @Autowired
    public HttpAiServiceExecutionAdapter(RestClient.Builder restClientBuilder) {
        this.restClient = Objects.requireNonNull(restClientBuilder, "restClientBuilder must not be null").build();
    }

    public HttpAiServiceExecutionAdapter(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    @Override
    public AiServiceExecutionResult execute(AiServiceExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        AiServiceResponse response = restClient.post()
                .uri(request.endpoint())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new AiServiceRequest(request.input()))
                .retrieve()
                .body(AiServiceResponse.class);

        if (response == null) {
            throw new IllegalArgumentException("AI service response must not be empty");
        }
        return new AiServiceExecutionResult(response.action());
    }

    private record AiServiceRequest(String input) {
    }

    private record AiServiceResponse(String action) {
    }
}
