package com.guardbench.testdefinition.presentation.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.guardbench.testdefinition.presentation.dto.TestCaseUpdateReq;

final class TestCaseUpdateReqValidator
        implements ConstraintValidator<ValidTestCaseUpdate, TestCaseUpdateReq> {

    @Override
    public boolean isValid(TestCaseUpdateReq value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (!value.hasAnyField()) {
            return false;
        }
        if (value.namePresent() && isBlank(value.name())) {
            return fieldError(context, "name", "이름은 필수입니다.");
        }
        if (value.inputPresent() && isBlank(value.input())) {
            return fieldError(context, "input", "입력은 필수입니다.");
        }
        if (value.expectedActionPresent() && value.expectedAction() == null) {
            return fieldError(context, "expectedAction", "기대 Action은 필수입니다.");
        }
        if (value.severityPresent() && value.severity() == null) {
            return fieldError(context, "severity", "Severity는 필수입니다.");
        }
        if (value.categoryPresent() && isBlank(value.category())) {
            return fieldError(context, "category", "category는 필수입니다.");
        }
        return true;
    }

    private boolean fieldError(
            ConstraintValidatorContext context, String field, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(field)
                .addConstraintViolation();
        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.codePoints().allMatch(this::isWhitespace);
    }

    private boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0xFEFF;
    }
}
