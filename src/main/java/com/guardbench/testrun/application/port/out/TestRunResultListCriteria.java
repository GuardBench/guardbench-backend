package com.guardbench.testrun.application.port.out;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.TestExecutionStatus;

public record TestRunResultListCriteria(
        String nameContains,
        String inputContains,
        String category,
        Action expectedAction,
        Severity severity,
        TestExecutionStatus executionStatus,
        String assertionStatusCode,
        String evaluationOutcomeCode,
        List<SortOrder<TestRunResultSortField>> sort,
        PageCriteria page) {
    private static final List<SortOrder<TestRunResultSortField>> DEFAULT_SORT = List.of(
            SortOrder.asc(TestRunResultSortField.SNAPSHOT_ID));

    public TestRunResultListCriteria {
        requireNonBlankIfPresent(nameContains, "nameContains");
        requireNonBlankIfPresent(inputContains, "inputContains");
        requireNonBlankIfPresent(category, "category");
        validateCode(assertionStatusCode, "assertionStatusCode", "PASS", "FAIL");
        validateCode(evaluationOutcomeCode, "evaluationOutcomeCode",
                "TRUE_POSITIVE", "TRUE_NEGATIVE", "FALSE_POSITIVE", "FALSE_NEGATIVE");
        Objects.requireNonNull(page, "page must not be null");
        sort = normalizeSort(sort);
    }

    public TestRunResultListCriteria(
            String nameContains,
            String inputContains,
            String category,
            Action expectedAction,
            Severity severity,
            TestExecutionStatus executionStatus,
            String assertionStatusCode,
            List<SortOrder<TestRunResultSortField>> sort,
            PageCriteria page) {
        this(nameContains, inputContains, category, expectedAction, severity, executionStatus,
                assertionStatusCode, null, sort, page);
    }

    public static TestRunResultListCriteria firstPage() {
        return new TestRunResultListCriteria(
                null, null, null, null, null, null, null, null, List.of(), PageCriteria.firstPage());
    }

    private static List<SortOrder<TestRunResultSortField>> normalizeSort(
            List<SortOrder<TestRunResultSortField>> requested) {
        Objects.requireNonNull(requested, "sort must not be null");
        if (requested.isEmpty()) {
            return DEFAULT_SORT;
        }
        if (requested.stream().anyMatch(order -> order.field() == TestRunResultSortField.SNAPSHOT_ID)) {
            return List.copyOf(requested);
        }
        List<SortOrder<TestRunResultSortField>> stable = new ArrayList<>(requested);
        stable.add(SortOrder.asc(TestRunResultSortField.SNAPSHOT_ID));
        return List.copyOf(stable);
    }

    private static void requireNonBlankIfPresent(String value, String field) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void validateCode(String value, String field, String... allowed) {
        if (value == null) {
            return;
        }
        for (String code : allowed) {
            if (code.equals(value)) {
                return;
            }
        }
        throw new IllegalArgumentException("unsupported " + field + "=" + value);
    }
}
