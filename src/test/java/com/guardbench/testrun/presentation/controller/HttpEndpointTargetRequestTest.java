package com.guardbench.testrun.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.guardbench.testrun.application.CreateTestRunService;
import com.guardbench.testrun.application.TestRunCreateCommand;
import com.guardbench.testrun.application.TestRunCreateResult;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TestRunCommandController.class)
class HttpEndpointTargetRequestTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean CreateTestRunService createTestRunService;

    @Test
    void mapsOpenAiCompatibleHttpEndpointAndRejectsInvalidCombinations() throws Exception {
        when(createTestRunService.create(any())).thenReturn(new TestRunCreateResult(
                902L, 1L, "QUEUED", 1, new com.guardbench.testrun.application.port.out.TargetReferenceView(
                        "target-ref", "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", null, "gpt-4o-mini"),
                new com.guardbench.testrun.domain.EvaluationProfile(java.util.List.of("PII_LEAKAGE"), "STANDARD"),
                Instant.parse("2026-08-24T14:30:00Z")));
        ArgumentCaptor<TestRunCreateCommand> captor = ArgumentCaptor.forClass(TestRunCreateCommand.class);
        String valid = "{\"testSuiteId\":1,\"target\":{\"type\":\"HTTP_ENDPOINT\",\"identifier\":\"https://example.com/v1/chat/completions\",\"model\":\"gpt-4o-mini\"},\"evaluationProfile\":{\"checks\":[\"PII_LEAKAGE\"],\"strictness\":\"STANDARD\"}}";

        mockMvc.perform(post("/api/v1/test-runs").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isAccepted());
        verify(createTestRunService).create(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("HTTP_ENDPOINT", captor.getValue().targetType());
        org.junit.jupiter.api.Assertions.assertEquals("https://example.com/v1/chat/completions", captor.getValue().targetIdentifier());
        org.junit.jupiter.api.Assertions.assertNull(captor.getValue().targetRevision());
        org.junit.jupiter.api.Assertions.assertEquals("gpt-4o-mini", captor.getValue().targetModel());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("PII_LEAKAGE"), captor.getValue().evaluationProfile().checks());

        String withRevision = "{\"testSuiteId\":1,\"target\":{\"type\":\"HTTP_ENDPOINT\",\"identifier\":\"https://example.com/v1/chat/completions\",\"revision\":\"1\",\"model\":\"gpt-4o-mini\"},\"evaluationProfile\":{\"checks\":[\"PII_LEAKAGE\"],\"strictness\":\"STANDARD\"}}";
        String missingModel = "{\"testSuiteId\":1,\"target\":{\"type\":\"HTTP_ENDPOINT\",\"identifier\":\"https://example.com/v1/chat/completions\"},\"evaluationProfile\":{\"checks\":[\"PII_LEAKAGE\"],\"strictness\":\"STANDARD\"}}";
        String invalidUrl = "{\"testSuiteId\":1,\"target\":{\"type\":\"HTTP_ENDPOINT\",\"identifier\":\"ftp://example.com/v1/chat/completions\",\"model\":\"gpt-4o-mini\"},\"evaluationProfile\":{\"checks\":[\"PII_LEAKAGE\"],\"strictness\":\"STANDARD\"}}";
        mockMvc.perform(post("/api/v1/test-runs").contentType(MediaType.APPLICATION_JSON).content(withRevision))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/v1/test-runs").contentType(MediaType.APPLICATION_JSON).content(missingModel))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post("/api/v1/test-runs").contentType(MediaType.APPLICATION_JSON).content(invalidUrl))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
    }
}
