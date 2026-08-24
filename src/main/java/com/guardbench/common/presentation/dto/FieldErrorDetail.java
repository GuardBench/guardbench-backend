package com.guardbench.common.presentation.dto;

/**
 * Validation 오류 하나를 표현한다. {@code field}는 DB Column이 아니라 외부 API 이름을 사용한다.
 *
 * <p>예: 중첩 필드 {@code candidate.guardrailId}, 배열 요소 {@code testCases[0].name}, Path·Query
 * {@code testRunId}·{@code page}, 반복 Query {@code sort[1]}, Header {@code Idempotency-Key}.
 */
public record FieldErrorDetail(String field, String message) {

    /**
     * 특정 필드에 귀속할 수 없는 요청 전체 오류에 사용하는 필드 이름이다.
     */
    public static final String REQUEST_FIELD = "_request";

    public static FieldErrorDetail ofRequest(String message) {
        return new FieldErrorDetail(REQUEST_FIELD, message);
    }
}
