package com.guardbench.testrun.application.port.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TestRunQueryPortContractTest {

    @Test
    void listCriteriaUsesCreatedAtDescendingAndIdDescendingByDefault() {
        TestRunListCriteria criteria = TestRunListCriteria.firstPage();

        assertEquals(List.of(
                SortOrder.desc(TestRunListSortField.CREATED_AT),
                SortOrder.desc(TestRunListSortField.ID)), criteria.sort());
        assertEquals(1, criteria.page().number());
        assertEquals(20, criteria.page().size());
    }

    @Test
    void listCriteriaAppendsStableIdSortAndAcceptsOnlyEvaluationStatusCodes() {
        TestRunListCriteria criteria = new TestRunListCriteria(
                1L,
                Set.of(),
                Set.of(),
                Set.of("PASS", "NOT_EVALUATED"),
                null,
                null,
                List.of(SortOrder.asc(TestRunListSortField.UPDATED_AT)),
                PageCriteria.firstPage());

        assertEquals(List.of(
                SortOrder.asc(TestRunListSortField.UPDATED_AT),
                SortOrder.desc(TestRunListSortField.ID)), criteria.sort());
        assertThrows(IllegalArgumentException.class, () -> new TestRunListCriteria(
                null, Set.of(), Set.of(), Set.of("UNKNOWN"), null, null, List.of(),
                PageCriteria.firstPage()));
    }

    @Test
    void qualityGateViewKeepsEvaluationValuesAsLocalScalarCodes() {
        QualityGateView evaluated = new QualityGateView(
                "PASS",
                new QualityGateMetricsView(0.95, 0L, 0.0, 0.05, 0.95));
        QualityGateView notEvaluated = new QualityGateView("NOT_EVALUATED", null);

        assertEquals("PASS", evaluated.statusCode());
        assertEquals("NOT_EVALUATED", notEvaluated.statusCode());
        assertThrows(IllegalArgumentException.class, () -> new QualityGateView("FAIL", null));
        assertThrows(IllegalArgumentException.class, () -> new QualityGateView(
                "NOT_EVALUATED", new QualityGateMetricsView(0.0, 0L, 0.0, 0.0, 0.0)));
    }

    @Test
    void resultCriteriaUsesSnapshotIdAscendingByDefaultAndResultsPreserveNullableEvaluationCodes() {
        TestRunResultListCriteria criteria = TestRunResultListCriteria.firstPage();
        TestRunResultItem item = new TestRunResultItem(
                10L,
                20L,
                "case",
                "input",
                com.guardbench.testrun.domain.Action.BLOCK,
                com.guardbench.testrun.domain.Severity.HIGH,
                "category",
                new TestExecutionView(com.guardbench.testrun.domain.TestExecutionStatus.FAILED,
                        null, "PROVIDER_ERROR", "safe message"),
                null);

        assertEquals(List.of(SortOrder.asc(TestRunResultSortField.SNAPSHOT_ID)), criteria.sort());
        assertFalse(item.assertionStatusCode() != null);
        assertThrows(IllegalArgumentException.class, () -> new TestRunResultItem(
                10L, 20L, "case", "input", com.guardbench.testrun.domain.Action.BLOCK,
                com.guardbench.testrun.domain.Severity.HIGH, "category", item.execution(), "UNKNOWN"));
    }

    @Test
    void pageResultKeepsRequestedPageForEmptyOutOfRangeResult() {
        PageResult<String> page = PageResult.of(List.of(), new PageCriteria(99, 20), 3L);

        assertEquals(99, page.number());
        assertEquals(1, page.totalPages());
        assertFalse(page.hasNext());
    }
}
