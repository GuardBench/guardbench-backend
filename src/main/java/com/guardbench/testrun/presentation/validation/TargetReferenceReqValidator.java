package com.guardbench.testrun.presentation.validation;

import java.net.URI;
import java.net.URISyntaxException;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import com.guardbench.testrun.presentation.dto.TargetReferenceReq;

/** Target type별 identifier와 revision 요청 규칙을 검증한다. */
public final class TargetReferenceReqValidator implements ConstraintValidator<ValidTargetReference, TargetReferenceReq> {
    private static final String HTTP_ENDPOINT = "HTTP_ENDPOINT";

    @Override
    public boolean isValid(TargetReferenceReq target, ConstraintValidatorContext context) {
        if (target == null || target.type() == null) return true;
        return switch (target.type()) {
            case HTTP_ENDPOINT -> (target.revision() == null || !target.revision().isBlank())
                    && (target.model() == null || !target.model().isBlank())
                    && isHttpUrl(target.identifier());
            default -> true;
        };
    }

    private static boolean isHttpUrl(String identifier) {
        if (identifier == null || identifier.isBlank()) return false;
        try {
            URI uri = new URI(identifier);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
