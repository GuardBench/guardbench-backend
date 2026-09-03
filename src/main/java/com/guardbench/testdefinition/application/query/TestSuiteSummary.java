package com.guardbench.testdefinition.application.query;

import java.time.Instant;
import java.util.Objects;

/**
 * TestSuite 목록 한 항목의 읽기 Projection이다.
 *
 * <p>Domain의 {@code TestSuite} Aggregate가 아니다. {@code testCaseCount}는 Aggregate가 관리하지 않고
 * 조회 시점에 TestCase를 집계한 값이므로, 그 값을 함께 담을 수 있는 별도 타입을 둔다.
 *
 * <p>{@code description}은 값이 없을 수 있다. 빈 문자열과 공백만 있는 값은 저장 시점에 이미 값이 없는
 * 것으로 정규화되므로 여기서는 {@code null}로만 표현된다.
 *
 * <p>근거: {@code docs/api/openapi.yaml},
 * {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
public record TestSuiteSummary(
        long id,
        String name,
        String description,
        long testCaseCount,
        Instant createdAt,
        Instant updatedAt) {

    public TestSuiteSummary {
        if (id <= 0) {
            throw new IllegalArgumentException("TestSuite 식별자는 양수여야 합니다. id=" + id);
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("TestSuite 이름은 비어 있을 수 없습니다.");
        }
        if (testCaseCount < 0) {
            throw new IllegalArgumentException(
                    "TestCase 개수는 음수일 수 없습니다. testCaseCount=" + testCaseCount);
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
