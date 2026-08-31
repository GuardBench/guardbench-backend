package com.guardbench.testrun.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * {@link TestRunCreateIntent}를 ADR 0008이 정의한 고정 필드 순서의 정규화된 문자열로 직렬화하고
 * SHA-256 hex fingerprint를 계산한다.
 *
 * <p>필드 구분자와 순서를 고정해, 같은 논리적 요청이면 필드 순서나 공백 차이와 무관하게 같은
 * fingerprint를 만든다.
 */
final class TestRunCreateFingerprint {

    private TestRunCreateFingerprint() {
    }

    static String of(TestRunCreateIntent intent) {
        String normalized = String.join(
                "\u0000",
                Long.toString(intent.testSuiteId()),
                intent.targetType(),
                intent.targetIdentifier(),
                intent.targetRevision() == null ? "" : intent.targetRevision(),
                intent.evaluationProfile() == null ? "" : String.join(",", intent.evaluationProfile().checks()),
                intent.evaluationProfile() == null ? "" : intent.evaluationProfile().strictness()
        );
        return sha256Hex(normalized);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm must be available", exception);
        }
    }
}
