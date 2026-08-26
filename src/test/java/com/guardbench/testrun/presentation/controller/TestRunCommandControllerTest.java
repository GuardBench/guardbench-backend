package com.guardbench.testrun.presentation.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.CreateTestRunService;
import com.guardbench.testrun.application.TestRunCreateCommand;
import com.guardbench.testrun.application.TestRunCreateResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * TestRun 비동기 접수 API의 MVC 계약을 검증한다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 OpenAPI 명세</a>
 */
@WebMvcTest(controllers = TestRunCommandController.class)
class TestRunCommandControllerTest {

    private static final String BASE = "/api/v1/test-runs";
    private static final String VALID_BODY = """
            {
              "testSuiteId": 1,
              "baseline": { "guardrailId": "guardrail-123", "version": "4" },
              "candidate": { "guardrailId": "guardrail-123", "source": "DRAFT" }
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateTestRunService createTestRunService;

    @Test
    @DisplayName("유효한 요청은 202와 Location 헤더, 접수된 TestRun을 반환한다")
    void createReturnsAcceptedWithLocationHeader() throws Exception {
        when(createTestRunService.create(any())).thenReturn(new TestRunCreateResult(
                901L, 1L, "QUEUED", 253, Instant.parse("2026-08-24T14:30:00Z")));

        mockMvc.perform(post(BASE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/test-runs/901"))
                .andExpect(jsonPath("$.httpStatus").value(202))
                .andExpect(jsonPath("$.data.id").value(901))
                .andExpect(jsonPath("$.data.testSuiteId").value(1))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.testCaseCount").value(253));
    }

    @Test
    @DisplayName("Idempotency-Key 헤더가 있으면 Command에 전달한다")
    void passesIdempotencyKeyToCommand() throws Exception {
        when(createTestRunService.create(any())).thenReturn(new TestRunCreateResult(
                901L, 1L, "QUEUED", 253, Instant.parse("2026-08-24T14:30:00Z")));
        ArgumentCaptor<TestRunCreateCommand> captor = ArgumentCaptor.forClass(TestRunCreateCommand.class);

        mockMvc.perform(post(BASE)
                        .header("Idempotency-Key", "31c83d18-12c4-47b7-9ed4-23e621cb9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isAccepted());

        verify(createTestRunService).create(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                "31c83d18-12c4-47b7-9ed4-23e621cb9999", captor.getValue().idempotencyKey());
    }

    @Test
    @DisplayName("testSuiteId가 없으면 400 VALIDATION_ERROR를 반환한다")
    void missingTestSuiteIdReturnsValidationError() throws Exception {
        String body = """
                {
                  "baseline": { "guardrailId": "guardrail-123", "version": "4" },
                  "candidate": { "guardrailId": "guardrail-123", "source": "DRAFT" }
                }
                """;

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("candidate.source가 DRAFT가 아니면 400 VALIDATION_ERROR를 반환한다")
    void nonDraftCandidateSourceReturnsValidationError() throws Exception {
        String body = """
                {
                  "testSuiteId": 1,
                  "baseline": { "guardrailId": "guardrail-123", "version": "4" },
                  "candidate": { "guardrailId": "guardrail-123", "source": "4" }
                }
                """;

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("baseline.version이 숫자가 아니면 400 VALIDATION_ERROR를 반환한다")
    void nonNumberedBaselineVersionReturnsValidationError() throws Exception {
        String body = """
                {
                  "testSuiteId": 1,
                  "baseline": { "guardrailId": "guardrail-123", "version": "DRAFT" },
                  "candidate": { "guardrailId": "guardrail-123", "source": "DRAFT" }
                }
                """;

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Idempotency-Key가 100자를 초과하면 400 VALIDATION_ERROR를 반환한다")
    void tooLongIdempotencyKeyReturnsValidationError() throws Exception {
        String tooLong = "a".repeat(101);

        mockMvc.perform(post(BASE)
                        .header("Idempotency-Key", tooLong)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("존재하지 않는 TestSuite면 404 TEST_SUITE_NOT_FOUND를 반환한다")
    void nonexistentTestSuiteReturnsNotFound() throws Exception {
        when(createTestRunService.create(any()))
                .thenThrow(new ApplicationException(ApplicationErrorCode.TEST_SUITE_NOT_FOUND));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("TEST_SUITE_NOT_FOUND"));
    }

    @Test
    @DisplayName("활성 TestCase가 없으면 409 TEST_SUITE_EMPTY를 반환한다")
    void emptyTestSuiteReturnsConflict() throws Exception {
        when(createTestRunService.create(any()))
                .thenThrow(new ApplicationException(ApplicationErrorCode.TEST_SUITE_EMPTY));

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("TEST_SUITE_EMPTY"));
    }

    @Test
    @DisplayName("같은 Idempotency-Key를 다른 요청에 재사용하면 409 IDEMPOTENCY_KEY_CONFLICT를 반환한다")
    void idempotencyKeyConflictReturnsConflict() throws Exception {
        when(createTestRunService.create(any()))
                .thenThrow(new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_KEY_CONFLICT));

        mockMvc.perform(post(BASE)
                        .header("Idempotency-Key", "31c83d18-12c4-47b7-9ed4-23e621cb9999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.data.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }
}
