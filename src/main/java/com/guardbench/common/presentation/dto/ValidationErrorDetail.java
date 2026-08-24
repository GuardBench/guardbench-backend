package com.guardbench.common.presentation.dto;

import java.util.List;

import com.guardbench.common.error.ApplicationErrorCode;

/**
 * Validation 오류 응답의 {@code data}다.
 *
 * <p>한 필드에 여러 오류가 발생할 수 있으므로 Map이 아닌 {@code errors[]}를 사용하고, 요청만으로 함께 확인할 수 있는
 * 오류는 한 응답에 함께 반환한다.
 */
public record ValidationErrorDetail(String code, List<FieldErrorDetail> errors) {

    public static ValidationErrorDetail of(List<FieldErrorDetail> errors) {
        return new ValidationErrorDetail(ApplicationErrorCode.VALIDATION_ERROR.code(), List.copyOf(errors));
    }
}
