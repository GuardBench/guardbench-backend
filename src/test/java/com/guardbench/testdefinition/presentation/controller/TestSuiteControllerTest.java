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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testdefinition.application.TestSuiteService;
import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.application.query.TestSuiteSummary;

/**
 * 승인된 TestSuite HTTP 계약을 MVC 경계에서 검증한다.
 *
 * @see <a href="../../../../../../../docs/api/openapi.yaml">OpenAPI</a>
 */
@WebMvcTest(controllers = TestSuiteController.class)
class TestSuiteControllerTest {

    private static final String BASE = "/api/v1/test-suites";
    private static final Instant NOW = Instant.parse("2026-08-26T01:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TestSuiteService testSuiteService;

    @Nested
    @DisplayName("생성 API")
    class CreateApi {

        @Test
        @DisplayName("유효한 초기 TestCase와 함께 생성하면 201과 Location을 반환한다")
        void createsSuiteWithInitialTestCases() throws Exception {
            when(testSuiteService.create(any())).thenReturn(summary(1L, "Safety", 1L));

            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "Safety",
                                      "description": "안전성",
                                      "testCases": [{
                                        "name": "PII 차단",
                                        "input": "개인정보를 알려줘",
                                        "expectedAction": "BLOCK",
                                        "severity": "CRITICAL",
                                        "category": "PII"
                                      }]
                                    }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/test-suites/1"))
                    .andExpect(jsonPath("$.httpStatus").value(201))
                    .andExpect(jsonPath("$.data.testCaseCount").value(1));
        }

        @Test
        @DisplayName("Unicode 공백뿐인 중첩 필드는 인덱스 경로로 Validation 오류를 반환한다")
        void rejectsUnicodeBlankNestedField() throws Exception {
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "Safety",
                                      "testCases": [{
                                        "name": "\u00a0",
                                        "input": "입력",
                                        "expectedAction": "BLOCK",
                                        "severity": "HIGH",
                                        "category": "PII"
                                      }]
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field")
                            .value(hasItem("testCases[0].name")));
        }

        @Test
        @DisplayName("초기 TestCase의 null 요소는 인덱스 경로로 Validation 오류를 반환한다")
        void rejectsNullInitialTestCaseElement() throws Exception {
            mockMvc.perform(post(BASE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "name": "Suite",
                                      "testCases": [null]
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field")
                            .value(hasItem("testCases[0]")));
        }
    }

    @Nested
    @DisplayName("목록 API")
    class ListApi {

        @Test
        @DisplayName("유효한 초과 페이지는 200과 빈 items 및 실제 집계값을 반환한다")
        void returnsEmptyItemsForOutOfRangePage() throws Exception {
            when(testSuiteService.list(any())).thenReturn(new PageResult<>(List.of(), 3, 20, 1));

            mockMvc.perform(get(BASE).param("page", "3"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items").isEmpty())
                    .andExpect(jsonPath("$.data.page.number").value(3))
                    .andExpect(jsonPath("$.data.page.totalElements").value(1));
        }

        @Test
        @DisplayName("허용되지 않은 정렬 조건은 sort 인덱스를 포함한 Validation 오류다")
        void rejectsInvalidSort() throws Exception {
            mockMvc.perform(get(BASE)
                            .param("sort", "updatedAt,desc")
                            .param("sort", "unknown,asc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("sort[1]")));
        }
    }

    @Nested
    @DisplayName("상세와 수정 API")
    class DetailAndUpdateApi {

        @Test
        @DisplayName("존재하지 않는 TestSuite는 404 TEST_SUITE_NOT_FOUND를 반환한다")
        void returnsNotFound() throws Exception {
            when(testSuiteService.get(99L)).thenThrow(
                    new ApplicationException(ApplicationErrorCode.TEST_SUITE_NOT_FOUND));

            mockMvc.perform(get(BASE + "/99"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.data.code").value("TEST_SUITE_NOT_FOUND"));
        }

        @Test
        @DisplayName("description의 명시적 null은 제거 요청으로 허용한다")
        void acceptsExplicitNullDescription() throws Exception {
            when(testSuiteService.update(eq(1L), any())).thenReturn(summary(1L, "Safety", 2L));

            mockMvc.perform(patch(BASE + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"description\":null}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.description").doesNotExist());
        }

        @Test
        @DisplayName("빈 수정 객체는 400 VALIDATION_ERROR를 반환한다")
        void rejectsEmptyUpdate() throws Exception {
            mockMvc.perform(patch(BASE + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("name의 명시적 null은 name 필드 Validation 오류를 반환한다")
        void rejectsExplicitNullName() throws Exception {
            mockMvc.perform(patch(BASE + "/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":null}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("name")));
        }

        @Test
        @DisplayName("TestSuite 삭제는 Body 없이 204를 반환한다")
        void deletesWithoutBody() throws Exception {
            doNothing().when(testSuiteService).delete(1L);

            mockMvc.perform(delete(BASE + "/1"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }
    }

    private static TestSuiteSummary summary(long id, String name, long testCaseCount) {
        return new TestSuiteSummary(id, name, null, testCaseCount, NOW, NOW);
    }
}
