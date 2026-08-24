package com.guardbench.common.presentation.dto;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

/**
 * 응답 Body가 존재하는 성공과 실패에 사용하는 공통 Envelope다. 204 No Content에는 Body가 없으므로 사용하지 않는다.
 *
 * <p>{@code httpStatus}는 실제 HTTP Status와 같은 숫자다. {@code message}는 사용자 안내용이며 클라이언트 분기
 * 기준이 아니다. 성공 {@code data}에는 API 전용 Response DTO를, 실패 {@code data}에는 안정적인 {@code code}를 가진
 * Error Detail을 둔다.
 *
 * @param <T> {@code data}에 담기는 Response DTO 또는 Error Detail 타입
 */
public record ApiResponse<T>(int httpStatus, String message, T data) {

    /**
     * 실제 응답 Status와 Body의 {@code httpStatus}를 같은 값에서 유도한다.
     */
    public static <T> ApiResponse<T> of(HttpStatusCode status, String message, T data) {
        return new ApiResponse<>(status.value(), message, data);
    }

    /**
     * 실제 HTTP Status와 Body의 {@code httpStatus} 불일치를 구조적으로 막기 위해 하나의 Status에서 둘을 함께 만든다.
     */
    public static <T> ResponseEntity<ApiResponse<T>> entity(HttpStatusCode status, String message, T data) {
        return ResponseEntity.status(status).body(of(status, message, data));
    }
}
