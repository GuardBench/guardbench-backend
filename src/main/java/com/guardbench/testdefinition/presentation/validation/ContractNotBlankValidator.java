package com.guardbench.testdefinition.presentation.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

final class ContractNotBlankValidator implements ConstraintValidator<ContractNotBlank, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null
                || value.codePoints().anyMatch(codePoint -> !isContractWhitespace(codePoint));
    }

    private boolean isContractWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0xFEFF;
    }
}
