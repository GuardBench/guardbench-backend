package com.guardbench.testrun.application.port.out;

public record TargetReferenceView(String referenceId, String type, String identifier, String revision, String model) {
    public TargetReferenceView {
        requireNonBlank(referenceId, "target reference ID");
        requireNonBlank(type, "target type");
        requireNonBlank(identifier, "target identifier");
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
    }
}
