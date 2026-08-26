package com.guardbench.testrun.presentation.controller;

import java.util.List;

import com.guardbench.common.presentation.dto.FieldErrorDetail;

/**
 * Query Parameter 형식 검증에 실패했을 때 던진다. {@link TestRunQueryController}가 이 예외를
 * {@code VALIDATION_ERROR} Envelope로 변환한다.
 *
 * @see <a href="../../../../../../../../docs/conventions/application-errors.md">애플리케이션 오류 코드</a>
 */
final class QueryParamValidationException extends RuntimeException {

    private final List<FieldErrorDetail> errors;

    QueryParamValidationException(List<FieldErrorDetail> errors) {
        super("Query Parameter 형식이 올바르지 않습니다.");
        this.errors = List.copyOf(errors);
    }

    List<FieldErrorDetail> errors() {
        return errors;
    }
}
