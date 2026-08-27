package com.guardbench.testdefinition.application.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testdefinition.domain.TestSuiteId;

/**
 * TestSuite 하나에 속한 TestCase 목록 조회 조건이다.
 *
 * <p>소속 TestSuite는 필수다. 승인된 API 계약이 TestCase 목록을 Suite 하위 경로로만 노출한다.
 *
 * <p>{@code nameContains}와 {@code inputContains}는 대소문자를 구분하지 않는 부분 일치이고,
 * {@code category}, {@code expectedAction}, {@code severity}는 정확히 일치다. 모든 filter는 선택이며
 * 여러 filter는 AND로 결합한다.
 *
 * <p>같은 Bounded Context이므로 {@link Action}과 {@link Severity}를 그대로 사용한다. 경계를 넘는
 * 계약이 아니어서 code 문자열로 낮출 이유가 없다.
 *
 * <p>정렬은 이 타입이 정규화한다. 비어 있으면 승인된 기본값을 적용하고, {@code ID}가 없으면 마지막
 * 조건으로 덧붙여 Offset Pagination이 흔들리지 않게 한다.
 *
 * <p>논리 삭제된 TestCase는 조회 대상이 아니다. 그 조건은 filter가 아니라 Port가 보장한다.
 *
 * <p>근거: {@code docs/api/openapi.yaml},
 * {@code docs/decisions/0006-independent-domain-contract-boundaries.md}
 */
public record TestCaseListCriteria(
        TestSuiteId testSuiteId,
        String nameContains,
        String inputContains,
        String category,
        Action expectedAction,
        Severity severity,
        Instant createdFrom,
        Instant createdTo,
        List<SortOrder<TestCaseSortField>> sort,
        PageCriteria page) {

    private static final List<SortOrder<TestCaseSortField>> DEFAULT_SORT = List.of(
            SortOrder.asc(TestCaseSortField.CREATED_AT),
            SortOrder.asc(TestCaseSortField.ID));

    public TestCaseListCriteria {
        Objects.requireNonNull(testSuiteId, "TestSuiteId must not be null");
        Objects.requireNonNull(page, "page must not be null");
        requireNonBlankIfPresent(nameContains, "name filter");
        requireNonBlankIfPresent(inputContains, "input filter");
        requireNonBlankIfPresent(category, "category filter");

        sort = normalize(sort);
    }

    /**
     * filter 없이 승인된 기본 정렬과 첫 페이지로 조회한다.
     */
    public static TestCaseListCriteria firstPage(TestSuiteId testSuiteId) {
        return new TestCaseListCriteria(
                testSuiteId, null, null, null, null, null, null, null,
                List.of(), PageCriteria.firstPage());
    }

    private static List<SortOrder<TestCaseSortField>> normalize(
            List<SortOrder<TestCaseSortField>> requested) {
        Objects.requireNonNull(requested, "sort must not be null");

        if (requested.isEmpty()) {
            return DEFAULT_SORT;
        }
        if (requested.stream().anyMatch(order -> order.field() == TestCaseSortField.ID)) {
            return List.copyOf(requested);
        }

        List<SortOrder<TestCaseSortField>> stable = new ArrayList<>(requested);
        stable.add(SortOrder.asc(TestCaseSortField.ID));

        return List.copyOf(stable);
    }

    private static void requireNonBlankIfPresent(String value, String label) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(label + "은 비어 있을 수 없습니다.");
        }
    }
}
