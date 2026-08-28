package com.guardbench.evaluation.infrastructure.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.guardbench.evaluation.application.ExecuteAiServiceAssertionService;
import com.guardbench.evaluation.domain.AssertionStatus;
import com.guardbench.evaluation.domain.EvaluationAction;
import com.guardbench.testrun.application.AiServiceExecutionFacade;
import com.guardbench.testrun.infrastructure.integration.aiservice.HttpAiServiceExecutionAdapter;

class AiServiceAssertionFlowTest {

    private static final String ENDPOINT = "https://customer.example/model";

    @Test
    void endpointResponsesFlowIntoAssertionWithoutBaselineComparison() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpAiServiceExecutionAdapter httpAdapter = new HttpAiServiceExecutionAdapter(builder.build());
        AiServiceExecutionFacade facade = new AiServiceExecutionFacade(httpAdapter);
        AiServiceActionIntegrationAdapter integrationAdapter = new AiServiceActionIntegrationAdapter(facade);
        ExecuteAiServiceAssertionService service = new ExecuteAiServiceAssertionService(integrationAdapter);

        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(content().json("{\"input\":\"허용 입력\"}"))
                .andRespond(withSuccess("{\"action\":\"ALLOW\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(ENDPOINT))
                .andExpect(method(POST))
                .andExpect(content().json("{\"input\":\"차단 입력\"}"))
                .andRespond(withSuccess("{\"action\":\"BLOCK\"}", MediaType.APPLICATION_JSON));

        List<ExecuteAiServiceAssertionService.Result> results = service.execute(
                ENDPOINT,
                List.of(
                        new ExecuteAiServiceAssertionService.Case("허용 입력", EvaluationAction.ALLOW),
                        new ExecuteAiServiceAssertionService.Case("차단 입력", EvaluationAction.BLOCK)
                )
        );

        assertThat(results).hasSize(2);
        assertThat(results.get(0).actualAction()).isEqualTo(EvaluationAction.ALLOW);
        assertThat(results.get(0).assertionResult().status()).isEqualTo(AssertionStatus.PASS);
        assertThat(results.get(1).actualAction()).isEqualTo(EvaluationAction.BLOCK);
        assertThat(results.get(1).assertionResult().status()).isEqualTo(AssertionStatus.PASS);
        server.verify();
    }

    @Test
    void mismatchedActionBecomesAssertionFail() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpAiServiceExecutionAdapter httpAdapter = new HttpAiServiceExecutionAdapter(builder.build());
        AiServiceExecutionFacade facade = new AiServiceExecutionFacade(httpAdapter);
        AiServiceActionIntegrationAdapter integrationAdapter = new AiServiceActionIntegrationAdapter(facade);
        ExecuteAiServiceAssertionService service = new ExecuteAiServiceAssertionService(integrationAdapter);

        server.expect(requestTo(ENDPOINT))
                .andRespond(withSuccess("{\"action\":\"ALLOW\"}", MediaType.APPLICATION_JSON));

        var result = service.execute(
                ENDPOINT,
                List.of(new ExecuteAiServiceAssertionService.Case("차단 기대 입력", EvaluationAction.BLOCK))
        ).getFirst();

        assertThat(result.actualAction()).isEqualTo(EvaluationAction.ALLOW);
        assertThat(result.assertionResult().status()).isEqualTo(AssertionStatus.FAIL);
        server.verify();
    }
}
