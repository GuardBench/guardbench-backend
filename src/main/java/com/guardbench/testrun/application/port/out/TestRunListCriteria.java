package com.guardbench.testrun.application.port.out;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.guardbench.testrun.domain.TestRunExecutionOutcome;
import com.guardbench.testrun.domain.TestRunStatus;

public record TestRunListCriteria(
        Long testSuiteId,
        Set<TestRunStatus> statuses,
        Set<TestRunExecutionOutcome> executionOutcomes,
        Set<String> qualityGateStatusCodes,
        Instant createdFrom,
        Instant createdTo,
        List<SortOrder<TestRunListSortField>> sort,
        PageCriteria page) {
    private static final List<SortOrder<TestRunListSortField>> DEFAULT_SORT = List.of(
            SortOrder.desc(TestRunListSortField.CREATED_AT),
            SortOrder.desc(TestRunListSortField.ID));

    public TestRunListCriteria {
        if (testSuiteId != null && testSuiteId <= 0) {
            throw new IllegalArgumentException("testSuiteId must be positive");
        }
        Objects.requireNonNull(statuses, "statuses must not be null");
        Objects.requireNonNull(executionOutcomes, "executionOutcomes must not be null");
        Objects.requireNonNull(qualityGateStatusCodes, "qualityGateStatusCodes must not be null");
        Objects.requireNonNull(page, "page must not be null");
        qualityGateStatusCodes.forEach(TestRunListCriteria::validateQualityGateStatusCode);
        statuses = Set.copyOf(statuses);
        executionOutcomes = Set.copyOf(executionOutcomes);
        qualityGateStatusCodes = Set.copyOf(qualityGateStatusCodes);
        sort = normalizeSort(sort);
    }

    public static TestRunListCriteria firstPage() {
        return new TestRunListCriteria(null, Set.of(), Set.of(), Set.of(), null, null, List.of(),
                PageCriteria.firstPage());
    }

    private static List<SortOrder<TestRunListSortField>> normalizeSort(
            List<SortOrder<TestRunListSortField>> requested) {
        Objects.requireNonNull(requested, "sort must not be null");
        if (requested.isEmpty()) {
            return DEFAULT_SORT;
        }
        if (requested.stream().anyMatch(order -> order.field() == TestRunListSortField.ID)) {
            return List.copyOf(requested);
        }
        List<SortOrder<TestRunListSortField>> stable = new ArrayList<>(requested);
        stable.add(SortOrder.desc(TestRunListSortField.ID));
        return List.copyOf(stable);
    }

    static void validateQualityGateStatusCode(String code) {
        if (!"PASS".equals(code) && !"FAIL".equals(code) && !"NOT_EVALUATED".equals(code)) {
            throw new IllegalArgumentException("unsupported Quality Gate status code=" + code);
        }
    }
}
