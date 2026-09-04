package com.guardbench.testrun.application.port.out;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
        Set<TestRunResultAttentionType> attentionTypes,
        boolean includeAttentionFacets,
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
        Objects.requireNonNull(attentionTypes, "attentionTypes must not be null");
        attentionTypes = Set.copyOf(attentionTypes);
        Objects.requireNonNull(page, "page must not be null");
        sort = normalizeSort(sort, attentionTypes);
    }

    public static TestRunResultListCriteria firstPage() {
        return new TestRunResultListCriteria(
                null, null, null, null, null, null, null, null,
                Set.of(), false, List.of(), PageCriteria.firstPage());
    }

    public boolean usesDefaultAttentionSort() {
        return !attentionTypes.isEmpty() && sort.isEmpty();
    }

    private static List<SortOrder<TestRunResultSortField>> normalizeSort(
            List<SortOrder<TestRunResultSortField>> requested,
            Set<TestRunResultAttentionType> attentionTypes) {
        Objects.requireNonNull(requested, "sort must not be null");
        if (requested.isEmpty()) {
            return attentionTypes.isEmpty() ? DEFAULT_SORT : List.of();
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
