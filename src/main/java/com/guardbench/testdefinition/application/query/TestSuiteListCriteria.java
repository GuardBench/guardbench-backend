package com.guardbench.testdefinition.application.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * TestSuite 목록 조회 조건이다.
 *
 * <p>모든 filter는 선택이며 {@code null}은 그 방향에 제한을 두지 않음을 뜻한다. 여러 filter는 AND로
 * 결합한다.
 *
 * <p>{@code createdFrom}은 해당 시각을 포함하고 {@code createdTo}는 포함하지 않는다. 승인된 계약의
 * 경계 정의를 그대로 따른다.
 *
 * <p>정렬은 이 타입이 정규화한다. 비어 있으면 승인된 기본값을 적용하고, {@code ID}가 없으면 마지막
 * 조건으로 덧붙인다. 안정 정렬이 없으면 같은 정렬 키를 가진 행의 순서가 페이지마다 달라져 Offset
 * Pagination이 항목을 건너뛰거나 중복시킬 수 있다. 호출자가 이 규칙을 빠뜨려도 계약이 유지되도록 조건
 * 타입에서 보장한다.
 *
 * <p>근거: {@code docs/api/openapi.yaml}
 */
public record TestSuiteListCriteria(
        String nameContains,
        Instant createdFrom,
        Instant createdTo,
        Long minTestCaseCount,
        Long maxTestCaseCount,
        List<SortOrder<TestSuiteSortField>> sort,
        PageCriteria page) {

    private static final List<SortOrder<TestSuiteSortField>> DEFAULT_SORT = List.of(
            SortOrder.desc(TestSuiteSortField.UPDATED_AT),
            SortOrder.desc(TestSuiteSortField.ID));

    public TestSuiteListCriteria {
        Objects.requireNonNull(page, "page must not be null");
        requireNonBlankIfPresent(nameContains, "name filter");
        requireNotNegativeIfPresent(minTestCaseCount, "minTestCaseCount");
        requireNotNegativeIfPresent(maxTestCaseCount, "maxTestCaseCount");

        sort = normalize(sort);
    }

    /**
     * filter 없이 승인된 기본 정렬과 첫 페이지로 조회한다.
     */
    public static TestSuiteListCriteria firstPage() {
        return new TestSuiteListCriteria(
                null, null, null, null, null, List.of(), PageCriteria.firstPage());
    }

    private static List<SortOrder<TestSuiteSortField>> normalize(
            List<SortOrder<TestSuiteSortField>> requested) {
        Objects.requireNonNull(requested, "sort must not be null");

        if (requested.isEmpty()) {
            return DEFAULT_SORT;
        }
        if (requested.stream().anyMatch(order -> order.field() == TestSuiteSortField.ID)) {
            return List.copyOf(requested);
        }

        List<SortOrder<TestSuiteSortField>> stable = new ArrayList<>(requested);
        stable.add(SortOrder.desc(TestSuiteSortField.ID));

        return List.copyOf(stable);
    }

    private static void requireNonBlankIfPresent(String value, String label) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(label + "은 비어 있을 수 없습니다.");
        }
    }

    private static void requireNotNegativeIfPresent(Long value, String label) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(label + "은 음수일 수 없습니다. value=" + value);
        }
    }
}
