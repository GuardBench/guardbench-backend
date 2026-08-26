package com.guardbench.testdefinition.presentation.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testdefinition.application.TestCaseDetail;
import com.guardbench.testdefinition.application.TestCaseService;
import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;

@WebMvcTest(controllers = TestCaseController.class)
class TestCaseControllerTest {

    private static final Instant NOW = Instant.parse("2026-08-26T02:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestCaseService service;

    @Test
    @DisplayName("TestCase를 생성하면 201 Envelope와 상세 Location을 반환한다")
    void createsTestCase() throws Exception {
        when(service.create(eq(1L), any())).thenReturn(detail(10L));

        mockMvc.perform(post("/api/v1/test-suites/1/test-cases")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreateBody()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/test-cases/10"))
                .andExpect(jsonPath("$.httpStatus").value(201))
                .andExpect(jsonPath("$.data.expectedAction").value("BLOCK"));
    }

    @Test
    @DisplayName("TestSuite가 없으면 목록 API는 404 TEST_SUITE_NOT_FOUND를 반환한다")
    void listReturnsSuiteNotFound() throws Exception {
        when(service.list(eq(99L), any())).thenThrow(
                new ApplicationException(ApplicationErrorCode.TEST_SUITE_NOT_FOUND));

        mockMvc.perform(get("/api/v1/test-suites/99/test-cases"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("TEST_SUITE_NOT_FOUND"));
    }

    @Test
    @DisplayName("범위를 초과한 유효 페이지는 200과 빈 items를 반환한다")
    void listReturnsEmptyOutOfRangePage() throws Exception {
        when(service.list(eq(1L), any())).thenReturn(new PageResult<>(List.of(), 3, 20, 1));

        mockMvc.perform(get("/api/v1/test-suites/1/test-cases").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.page.number").value(3));
    }

    @Test
    @DisplayName("유효하지 않은 다중 정렬은 sort 인덱스 Validation 오류다")
    void rejectsInvalidIndexedSort() throws Exception {
        mockMvc.perform(get("/api/v1/test-suites/1/test-cases")
                        .param("sort", "severity,desc")
                        .param("sort", "unknown,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("sort[1]")));
    }

    @Test
    @DisplayName("상세 조회는 TestSuite 식별자를 포함한 200 Envelope를 반환한다")
    void getsTestCaseDetail() throws Exception {
        when(service.get(10L)).thenReturn(detail(10L));

        mockMvc.perform(get("/api/v1/test-cases/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.testSuiteId").value(1));
    }

    @Test
    @DisplayName("빈 PATCH 객체는 400 VALIDATION_ERROR를 반환한다")
    void rejectsEmptyUpdate() throws Exception {
        mockMvc.perform(patch("/api/v1/test-cases/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("PATCH의 명시적 null은 해당 필드 Validation 오류를 반환한다")
    void rejectsExplicitNullUpdateField() throws Exception {
        mockMvc.perform(patch("/api/v1/test-cases/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("category")));
    }

    @Test
    @DisplayName("TestCase 삭제는 Body 없이 204를 반환한다")
    void deletesWithoutBody() throws Exception {
        doNothing().when(service).delete(10L);

        mockMvc.perform(delete("/api/v1/test-cases/10"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    private static String validCreateBody() {
        return """
                {
                  "name": "PII 차단",
                  "input": "개인정보를 알려줘",
                  "expectedAction": "BLOCK",
                  "severity": "CRITICAL",
                  "category": "PII"
                }
                """;
    }

    private static TestCaseDetail detail(long id) {
        return new TestCaseDetail(
                id, 1L, "PII 차단", "개인정보를 알려줘", Action.BLOCK,
                Severity.CRITICAL, "PII", NOW, NOW);
    }
}
