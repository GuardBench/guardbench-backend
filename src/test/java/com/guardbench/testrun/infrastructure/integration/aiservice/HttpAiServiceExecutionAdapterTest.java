package com.guardbench.testrun.infrastructure.integration.aiservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.guardbench.testrun.application.AiServiceResultNormalizer;
import com.guardbench.testrun.application.port.out.AiServiceExecutionRequest;
import com.guardbench.testrun.application.port.out.AiServiceExecutionResult;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.ActualResult;

class HttpAiServiceExecutionAdapterTest {

    private static final String ENDPOINT = "https://customer.example/model";
    private static final String INPUT = "테스트할 문자열";

    @Test
    @DisplayName("200 + ALLOW 응답은 input 하나만 POST하고 ActualResult(ALLOW)로 변환한다")
    void allowResponseBecomesAllowActualResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpAiServiceExecutionAdapter adapter = new HttpAiServiceExecutionAdapter(builder.build());
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"input\":\"" + INPUT + "\"}"))
                .andRespond(withSuccess("{\"action\":\"ALLOW\"}", MediaType.APPLICATION_JSON));

        AiServiceExecutionResult result = adapter.execute(new AiServiceExecutionRequest(ENDPOINT, INPUT));
        ActualResult actualResult = AiServiceResultNormalizer.normalize(result);

        assertThat(actualResult.action()).isEqualTo(Action.ALLOW);
        server.verify();
    }

    @Test
    @DisplayName("200 + BLOCK 응답은 input 하나만 POST하고 ActualResult(BLOCK)로 변환한다")
    void blockResponseBecomesBlockActualResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpAiServiceExecutionAdapter adapter = new HttpAiServiceExecutionAdapter(builder.build());
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().string("{\"input\":\"" + INPUT + "\"}"))
                .andRespond(withSuccess("{\"action\":\"BLOCK\"}", MediaType.APPLICATION_JSON));

        AiServiceExecutionResult result = adapter.execute(new AiServiceExecutionRequest(ENDPOINT, INPUT));
        ActualResult actualResult = AiServiceResultNormalizer.normalize(result);

        assertThat(actualResult.action()).isEqualTo(Action.BLOCK);
        server.verify();
    }
}
