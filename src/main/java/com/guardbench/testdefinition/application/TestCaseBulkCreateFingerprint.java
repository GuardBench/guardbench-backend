package com.guardbench.testdefinition.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class TestCaseBulkCreateFingerprint {

    private TestCaseBulkCreateFingerprint() {
    }

    static String of(long testSuiteId, TestCaseBulkCreateCommand command) {
        StringBuilder normalized = new StringBuilder(Long.toString(testSuiteId));
        for (TestCaseCreateCommand item : command.items()) {
            append(normalized, item.name());
            append(normalized, item.input());
            append(normalized, item.expectedAction().name());
            append(normalized, item.severity().name());
            append(normalized, item.category());
        }
        return sha256Hex(normalized.toString());
    }

    private static void append(StringBuilder target, String value) {
        target.append(':').append(value.length()).append(':').append(value);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm must be available", exception);
        }
    }
}
