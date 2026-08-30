package com.guardbench.testrun.application.port.out;

public record TargetReferenceView(String referenceId) {

    public TargetReferenceView {
        requireNonBlank(referenceId, "target reference ID");
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
