package com.guardbench.common.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class HealthRuntimeIntegrationTest {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @LocalServerPort
    private int serverPort;

    @Test
    @DisplayName("전체 애플리케이션 context의 실제 HTTP 서버에서 GET /health가 200을 반환한다")
    void returnsOkFromFullApplicationRuntime() throws Exception {
        assertThat(applicationContext.getBeansOfType(HealthController.class))
                .as("HealthController must be discovered by component scanning")
                .hasSize(1);
        assertThat(handlerMapping.getHandlerMethods().values())
                .as("HealthController must be registered in handler mappings")
                .anySatisfy(handlerMethod -> assertThat(handlerMethod.getBeanType()).isEqualTo(HealthController.class));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:%d/health".formatted(serverPort)))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEmpty();
    }
}
