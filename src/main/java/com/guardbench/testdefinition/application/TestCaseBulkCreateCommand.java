package com.guardbench.testdefinition.application;

import java.util.List;
import java.util.Objects;

public record TestCaseBulkCreateCommand(
        String idempotencyKey,
        List<TestCaseCreateCommand> items) {

    public TestCaseBulkCreateCommand {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new IllegalArgumentException("idempotencyKey must be between 1 and 100 characters");
        }
        Objects.requireNonNull(items, "items must not be null");
        if (items.isEmpty() || items.size() > 1000) {
            throw new IllegalArgumentException("items size must be between 1 and 1000");
        }
        items = List.copyOf(items);
    }
}
