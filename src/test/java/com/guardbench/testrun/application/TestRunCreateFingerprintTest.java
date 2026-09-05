package com.guardbench.testrun.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestRunCreateFingerprintTest {

    @Test
    @DisplayName("기본 Quality Gate 정책은 기존 fingerprint와 호환된다")
    void defaultPolicyPreservesLegacyFingerprint() {
        TestRunCreateIntent intent = new TestRunCreateIntent(
                1L,
                "HTTP_ENDPOINT",
                "https://example.com/v1/chat/completions",
                "v1",
                "test-model",
                0.95,
                0.95);

        assertEquals(
                "1e6788df7c4cafbe5b5b763e2a703c02689000b71b71353d9053d87b2d106f8c",
                TestRunCreateFingerprint.of(intent));
    }

    @Test
    @DisplayName("사용자 지정 Quality Gate 정책은 fingerprint를 구분한다")
    void customPolicyChangesFingerprint() {
        TestRunCreateIntent first = new TestRunCreateIntent(
                1L, "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", "test-model",
                0.9, 0.98);
        TestRunCreateIntent second = new TestRunCreateIntent(
                1L, "HTTP_ENDPOINT", "https://example.com/v1/chat/completions", "v1", "test-model",
                0.95, 0.98);

        assertNotEquals(TestRunCreateFingerprint.of(first), TestRunCreateFingerprint.of(second));
    }
}
