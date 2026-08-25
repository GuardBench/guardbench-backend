package com.guardbench.testdefinition.application.query;

import java.time.Instant;
import java.util.Objects;

import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;

/**
 * TestCase 목록 한 항목의 읽기 Projection이다.
 *
 * <p>소속 {@code TestSuiteId}를 담지 않는다. 승인된 API 계약이 부모 TestSuite를 경로로 식별하므로 항목마다
 * 중복해서 반환하지 않는다.
 *
 * <p>{@code deletedAt}도 담지 않는다. 이 Projection은 활성 TestCase만 표현한다.
 *
 * <p>근거: {@code docs/api/openapi.yaml}
 */
public record TestCaseSummary(
        long id,
        String name,
        String input,
        Action expectedAction,
        Severity severity,
        String category,
        Instant createdAt,
        Instant updatedAt) {

    public TestCaseSummary {
        if (id <= 0) {
            throw new IllegalArgumentException("TestCase 식별자는 양수여야 합니다. id=" + id);
        }
        requireNonBlank(name, "이름");
        requireNonBlank(input, "입력");
        requireNonBlank(category, "category");
        Objects.requireNonNull(expectedAction, "expectedAction must not be null");
        Objects.requireNonNull(severity, "severity must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TestCase " + label + "은 비어 있을 수 없습니다.");
        }
    }
}
