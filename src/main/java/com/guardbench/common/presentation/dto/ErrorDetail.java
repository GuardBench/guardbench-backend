package com.guardbench.common.presentation.dto;

import com.guardbench.common.error.ApplicationErrorCode;

/**
 * Validation을 제외한 오류 응답의 {@code data}다. 클라이언트는 {@code message}가 아니라 이 {@code code}로 분기한다.
 */
public record ErrorDetail(String code) {

    public static ErrorDetail of(ApplicationErrorCode errorCode) {
        return new ErrorDetail(errorCode.code());
    }
}
