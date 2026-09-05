package com.guardbench.testrun.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.guardbench.testrun.domain.QualityGatePolicy;

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
        List<String> fields = new ArrayList<>(List.of(
                Long.toString(intent.testSuiteId()),
                intent.targetType(),
                intent.targetIdentifier(),
                intent.targetRevision() == null ? "" : intent.targetRevision(),
                intent.targetModel() == null ? "" : intent.targetModel()));
        if (!usesDefaultPolicy(intent)) {
            fields.add(Double.toString(intent.assertionPassRateThreshold()));
            fields.add(Double.toString(intent.executionSuccessRateThreshold()));
        }
        String normalized = String.join("\u0000", fields);
        return sha256Hex(normalized);
    }

    private static boolean usesDefaultPolicy(TestRunCreateIntent intent) {
        return intent.assertionPassRateThreshold()
                        == QualityGatePolicy.DEFAULT_ASSERTION_PASS_RATE_THRESHOLD
                && intent.executionSuccessRateThreshold()
                        == QualityGatePolicy.DEFAULT_EXECUTION_SUCCESS_RATE_THRESHOLD;
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
