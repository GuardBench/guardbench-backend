package com.guardbench.testdefinition.presentation.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.guardbench.testdefinition.presentation.dto.TestSuiteUpdateReq;

final class TestSuiteUpdateReqValidator
        implements ConstraintValidator<ValidTestSuiteUpdate, TestSuiteUpdateReq> {

    @Override
    public boolean isValid(TestSuiteUpdateReq value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }
        if (!value.namePresent() && !value.descriptionPresent()) {
            return false;
        }
        if (value.namePresent() && isContractBlank(value.name())) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate("이름은 필수입니다.")
                    .addPropertyNode("name")
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

    private boolean isContractBlank(String value) {
        return value == null
                || value.codePoints().allMatch(this::isContractWhitespace);
    }

    private boolean isContractWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0xFEFF;
    }
}
