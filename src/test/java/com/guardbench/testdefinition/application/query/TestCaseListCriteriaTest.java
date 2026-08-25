package com.guardbench.testdefinition.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testdefinition.domain.TestSuiteId;

class TestCaseListCriteriaTest {

    private static final TestSuiteId TEST_SUITE_ID = new TestSuiteId(7L);

    @Nested
    @DisplayName("정렬 정규화")
    class SortNormalization {

        @Test
        @DisplayName("정렬을 지정하지 않으면 승인된 기본 정렬을 적용한다")
        void appliesApprovedDefaultSortWhenOmitted() {
            TestCaseListCriteria criteria = criteriaWithSort(List.of());

            assertEquals(
                    List.of(
                            SortOrder.asc(TestCaseSortField.CREATED_AT),
                            SortOrder.asc(TestCaseSortField.ID)),
                    criteria.sort());
        }

        @Test
        @DisplayName("id 조건이 없으면 마지막 보조 정렬로 id asc를 덧붙인다")
        void appendsIdAsLastTieBreaker() {
            TestCaseListCriteria criteria =
                    criteriaWithSort(List.of(SortOrder.desc(TestCaseSortField.SEVERITY)));

            assertEquals(
                    List.of(
                            SortOrder.desc(TestCaseSortField.SEVERITY),
                            SortOrder.asc(TestCaseSortField.ID)),
                    criteria.sort());
        }

        @Test
        @DisplayName("id 조건이 이미 있으면 순서를 바꾸거나 덧붙이지 않는다")
        void keepsRequestedSortWhenIdAlreadyPresent() {
            List<SortOrder<TestCaseSortField>> requested = List.of(
                    SortOrder.desc(TestCaseSortField.ID),
                    SortOrder.asc(TestCaseSortField.NAME));

            assertEquals(requested, criteriaWithSort(requested).sort());
        }
    }

    @Nested
    @DisplayName("조건 검증")
    class Validation {

        @Test
        @DisplayName("소속 TestSuite만으로 첫 페이지 조회를 만들 수 있다")
        void createsUnfilteredFirstPage() {
            TestCaseListCriteria criteria = TestCaseListCriteria.firstPage(TEST_SUITE_ID);

            assertEquals(TEST_SUITE_ID, criteria.testSuiteId());
            assertNull(criteria.nameContains());
            assertEquals(1, criteria.page().number());
        }

        @Test
        @DisplayName("Enum filter를 Domain 타입으로 그대로 보유한다")
        void keepsEnumFiltersAsDomainTypes() {
            TestCaseListCriteria criteria = new TestCaseListCriteria(
                    TEST_SUITE_ID, null, null, "PII", Action.BLOCK, Severity.CRITICAL,
                    null, null, List.of(), PageCriteria.firstPage());

            assertEquals(Action.BLOCK, criteria.expectedAction());
            assertEquals(Severity.CRITICAL, criteria.severity());
            assertEquals("PII", criteria.category());
        }

        @Test
        @DisplayName("소속 TestSuite가 null이면 NullPointerException을 던진다")
        void rejectsNullTestSuiteId() {
            assertThrows(
                    NullPointerException.class,
                    () -> new TestCaseListCriteria(
                            null, null, null, null, null, null, null, null,
                            List.of(), PageCriteria.firstPage()));
        }

        @Test
        @DisplayName("입력 filter가 공백만 있으면 IllegalArgumentException을 던진다")
        void rejectsBlankInputFilter() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TestCaseListCriteria(
                            TEST_SUITE_ID, null, "   ", null, null, null, null, null,
                            List.of(), PageCriteria.firstPage()));
        }

        @Test
        @DisplayName("category filter가 공백만 있으면 IllegalArgumentException을 던진다")
        void rejectsBlankCategoryFilter() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TestCaseListCriteria(
                            TEST_SUITE_ID, null, null, "   ", null, null, null, null,
                            List.of(), PageCriteria.firstPage()));
        }
    }

    private static TestCaseListCriteria criteriaWithSort(
            List<SortOrder<TestCaseSortField>> sort) {
        return new TestCaseListCriteria(
                TEST_SUITE_ID, null, null, null, null, null, null, null,
                sort, PageCriteria.firstPage());
    }
}
