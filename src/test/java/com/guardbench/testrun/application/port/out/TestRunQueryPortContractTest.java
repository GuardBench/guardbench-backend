package com.guardbench.testrun.application.port.out;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

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
                new QualityGateMetricsView(
                        new QualityGateMetricView(0.95, 0.95, true),
                        new QualityGateMetricView(0.95, 0.95, true)));
        QualityGateView notEvaluated = new QualityGateView("NOT_EVALUATED", null);

        assertEquals("PASS", evaluated.statusCode());
        assertEquals("NOT_EVALUATED", notEvaluated.statusCode());
        assertThrows(IllegalArgumentException.class, () -> new QualityGateView("FAIL", null));
        assertThrows(IllegalArgumentException.class, () -> new QualityGateView(
                "NOT_EVALUATED", new QualityGateMetricsView(
                        new QualityGateMetricView(0.0, 0.95, false),
                        new QualityGateMetricView(0.0, 0.95, false))));
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
                        null, "APPLICATION_TARGET", "PROVIDER_ERROR", "safe message"),
                null, null, TestRunResultAttentionType.EXECUTION_FAILED);

        assertEquals(List.of(SortOrder.asc(TestRunResultSortField.SNAPSHOT_ID)), criteria.sort());
        assertFalse(item.assertionStatusCode() != null);
        assertThrows(IllegalArgumentException.class, () -> new TestRunResultItem(
                10L, 20L, "case", "input", com.guardbench.testrun.domain.Action.BLOCK,
                com.guardbench.testrun.domain.Severity.HIGH, "category", item.execution(), "UNKNOWN", null,
                TestRunResultAttentionType.EXECUTION_FAILED));
    }

    @Test
    @DisplayName("Attention Filter에 명시 정렬이 없으면 전용 기본 정렬을 위해 정렬 목록을 비워 둔다")
    void resultCriteriaKeepsSortEmptyForAttentionDefaultOrder() {
        TestRunResultListCriteria criteria = new TestRunResultListCriteria(
                null, null, null, null, null, null, null, null,
                Set.of(TestRunResultAttentionType.FALSE_NEGATIVE), false, List.of(),
                PageCriteria.firstPage());

        assertEquals(List.of(), criteria.sort());
        assertEquals(true, criteria.usesDefaultAttentionSort());
    }

    @Test
    @DisplayName("처리 실패 분류는 평가 분류보다 우선하고 성공 결과만 FN 또는 FP가 된다")
    void attentionTypePrioritizesProcessingStateOverEvaluation() {
        assertEquals(TestRunResultAttentionType.EXECUTION_FAILED,
                TestRunResultAttentionType.classify(
                        com.guardbench.testrun.domain.TestExecutionStatus.FAILED,
                        com.guardbench.testrun.domain.Action.BLOCK,
                        com.guardbench.testrun.domain.Action.ALLOW));
        assertEquals(TestRunResultAttentionType.FALSE_NEGATIVE,
                TestRunResultAttentionType.classify(
                        com.guardbench.testrun.domain.TestExecutionStatus.SUCCEEDED,
                        com.guardbench.testrun.domain.Action.BLOCK,
                        com.guardbench.testrun.domain.Action.ALLOW));
        assertEquals(null,
                TestRunResultAttentionType.classify(
                        com.guardbench.testrun.domain.TestExecutionStatus.SUCCEEDED,
                        com.guardbench.testrun.domain.Action.BLOCK,
                        com.guardbench.testrun.domain.Action.BLOCK));
    }

    @Test
    void pageResultKeepsRequestedPageForEmptyOutOfRangeResult() {
        PageResult<String> page = PageResult.of(List.of(), new PageCriteria(99, 20), 3L);

        assertEquals(99, page.number());
        assertEquals(1, page.totalPages());
        assertFalse(page.hasNext());
    }
}
