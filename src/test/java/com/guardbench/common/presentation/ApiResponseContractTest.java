package com.guardbench.common.presentation;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 승인된 공통 응답·오류 계약이 MVC 경계에서 실제로 표현되는지 검증한다.
 *
 * @see <a href="../../../../../../docs/conventions/api-response.md">API 공통 응답 DTO</a>
 */
@WebMvcTest(controllers = ApiFoundationProbeController.class)
class ApiResponseContractTest {

    private static final String BASE = "/api-foundation-probe";

    @Autowired
    private MockMvc mockMvc;

    @Nested
    @DisplayName("성공 응답")
    class SuccessResponse {

        @Test
        @DisplayName("201 응답의 httpStatus는 실제 HTTP Status와 같다")
        void createdResponseMatchesHttpStatus() throws Exception {
            mockMvc.perform(post(BASE + "/probes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"safety\"}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.httpStatus").value(201))
                    .andExpect(jsonPath("$.message").value(ApiFoundationProbeController.SUCCESS_MESSAGE))
                    .andExpect(jsonPath("$.data.name").value("safety"));
        }

        @Test
        @DisplayName("200 응답의 httpStatus는 실제 HTTP Status와 같다")
        void okResponseMatchesHttpStatus() throws Exception {
            mockMvc.perform(get(BASE + "/probes/901"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.httpStatus").value(200))
                    .andExpect(jsonPath("$.data.name").value("probe-901"));
        }

        @Test
        @DisplayName("202 응답의 httpStatus는 실제 HTTP Status와 같다")
        void acceptedResponseMatchesHttpStatus() throws Exception {
            mockMvc.perform(post(BASE + "/accepted"))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.httpStatus").value(202));
        }

        @Test
        @DisplayName("204 응답에는 공통 Envelope를 포함한 Body가 없다")
        void noContentResponseHasNoBody() throws Exception {
            mockMvc.perform(delete(BASE + "/probes/901"))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
        }
    }

    @Nested
    @DisplayName("Validation 오류")
    class ValidationErrorResponse {

        @Test
        @DisplayName("필수 필드 누락은 VALIDATION_ERROR와 필드 경로를 반환한다")
        void missingRequiredFieldReturnsValidationError() throws Exception {
            mockMvc.perform(post(BASE + "/probes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.httpStatus").value(400))
                    .andExpect(jsonPath("$.message").value("요청 값이 올바르지 않습니다."))
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("name")))
                    .andExpect(jsonPath("$.data.errors[*].message").value(hasItem("이름은 필수입니다.")));
        }

        @Test
        @DisplayName("중첩 필드와 배열 요소는 외부 API 필드 경로로 표현한다")
        void nestedAndIndexedFieldPaths() throws Exception {
            mockMvc.perform(post(BASE + "/probes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"safety\",\"candidate\":{\"guardrailId\":\" \"},"
                                    + "\"testCases\":[{\"guardrailId\":\"\"}]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("candidate.guardrailId")))
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("testCases[0].guardrailId")));
        }

        @Test
        @DisplayName("알 수 없는 Request Body 필드는 거부한다")
        void unknownRequestBodyFieldIsRejected() throws Exception {
            mockMvc.perform(post(BASE + "/probes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"safety\",\"unknownField\":1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.httpStatus").value(400))
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("unknownField")));
        }

        @Test
        @DisplayName("중첩 객체의 알 수 없는 필드도 경로와 함께 거부한다")
        void unknownNestedRequestBodyFieldIsRejected() throws Exception {
            mockMvc.perform(post(BASE + "/probes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"safety\",\"candidate\":{\"guardrailId\":\"g-1\",\"source\":\"DRAFT\"}}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("candidate.source")));
        }

        @Test
        @DisplayName("읽을 수 없는 Body는 _request 오류로 반환한다")
        void unreadableBodyReturnsRequestScopedError() throws Exception {
            mockMvc.perform(post(BASE + "/probes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("_request")));
        }

        @Test
        @DisplayName("유효하지 않은 Path ID는 VALIDATION_ERROR를 반환한다")
        void invalidPathIdReturnsValidationError() throws Exception {
            mockMvc.perform(get(BASE + "/probes/not-a-number"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.httpStatus").value(400))
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("probeId")));
        }

        @Test
        @DisplayName("Pagination 범위 위반은 Query Parameter 이름으로 보고한다")
        void invalidQueryParameterReturnsValidationError() throws Exception {
            mockMvc.perform(get(BASE + "/probes").param("page", "0").param("sort", "asc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("page")))
                    .andExpect(jsonPath("$.data.errors[*].message").value(hasItem("page는 1 이상이어야 합니다.")));
        }

        @Test
        @DisplayName("반복 Query Parameter 오류는 인덱스를 포함한다")
        void repeatedQueryParameterReportsIndex() throws Exception {
            mockMvc.perform(get(BASE + "/probes")
                            .param("page", "1")
                            .param("sort", "asc")
                            .param("sort", "sideways"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("sort[1]")));
        }

        @Test
        @DisplayName("필수 Query Parameter 누락은 해당 이름으로 보고한다")
        void missingQueryParameterReturnsValidationError() throws Exception {
            mockMvc.perform(get(BASE + "/probes").param("sort", "asc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("page")));
        }

        @Test
        @DisplayName("필수 Header 누락은 Header 이름으로 보고한다")
        void missingHeaderReturnsValidationError() throws Exception {
            mockMvc.perform(get(BASE + "/idempotent"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.data.code").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.data.errors[*].field").value(hasItem("Idempotency-Key")));
        }
    }

    @Nested
    @DisplayName("Application Error")
    class ApplicationErrorResponse {

        @Test
        @DisplayName("등록되지 않은 API 경로는 ENDPOINT_NOT_FOUND를 반환한다")
        void unknownEndpointReturnsEndpointNotFound() throws Exception {
            mockMvc.perform(get("/unknown-api-endpoint"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.httpStatus").value(404))
                    .andExpect(jsonPath("$.message").value("요청한 API Endpoint를 찾을 수 없습니다."))
                    .andExpect(jsonPath("$.data.code").value("ENDPOINT_NOT_FOUND"));
        }

        @Test
        @DisplayName("지원하지 않는 Method는 METHOD_NOT_ALLOWED와 Allow 헤더를 반환한다")
        void unsupportedMethodReturnsMethodNotAllowed() throws Exception {
            mockMvc.perform(get(BASE + "/accepted"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", "POST"))
                    .andExpect(jsonPath("$.httpStatus").value(405))
                    .andExpect(jsonPath("$.data.code").value("METHOD_NOT_ALLOWED"));
        }

        @Test
        @DisplayName("제공할 수 없는 응답 형식은 NOT_ACCEPTABLE을 반환한다")
        void unsupportedAcceptReturnsNotAcceptable() throws Exception {
            mockMvc.perform(get(BASE + "/probes/901").accept(MediaType.APPLICATION_XML))
                    .andExpect(status().isNotAcceptable())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.httpStatus").value(406))
                    .andExpect(jsonPath("$.data.code").value("NOT_ACCEPTABLE"));
        }

        @Test
        @DisplayName("지원하지 않는 요청 형식은 UNSUPPORTED_MEDIA_TYPE을 반환한다")
        void unsupportedContentTypeReturnsUnsupportedMediaType() throws Exception {
            mockMvc.perform(post(BASE + "/probes")
                            .contentType(MediaType.TEXT_PLAIN)
                            .content("safety"))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.httpStatus").value(415))
                    .andExpect(jsonPath("$.data.code").value("UNSUPPORTED_MEDIA_TYPE"));
        }

        @Test
        @DisplayName("404 Application Error는 Code와 HTTP Status가 일치한다")
        void notFoundReturnsApprovedCode() throws Exception {
            mockMvc.perform(get(BASE + "/not-found"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.httpStatus").value(404))
                    .andExpect(jsonPath("$.message").value("TestRun을 찾을 수 없습니다."))
                    .andExpect(jsonPath("$.data.code").value("TEST_RUN_NOT_FOUND"));
        }

        @Test
        @DisplayName("409 Application Error는 Code와 HTTP Status가 일치한다")
        void conflictReturnsApprovedCode() throws Exception {
            mockMvc.perform(get(BASE + "/conflict"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.httpStatus").value(409))
                    .andExpect(jsonPath("$.data.code").value("TEST_RUN_NOT_FINISHED"));
        }

        @Test
        @DisplayName("예상하지 못한 예외는 내부 정보 없이 INTERNAL_SERVER_ERROR로 변환한다")
        void unexpectedExceptionIsMaskedAsInternalServerError() throws Exception {
            mockMvc.perform(get(BASE + "/unexpected"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.httpStatus").value(500))
                    .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."))
                    .andExpect(jsonPath("$.data.code").value("INTERNAL_SERVER_ERROR"))
                    .andExpect(content().string(not(containsString("IllegalStateException"))))
                    .andExpect(content().string(not(containsString("내부 전용 진단 문구와 비밀정보"))))
                    .andExpect(content().string(not(containsString("com.guardbench.common.presentation"))));
        }
    }
}
