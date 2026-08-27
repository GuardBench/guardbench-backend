package com.guardbench.testdefinition.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TestSuiteListCriteriaTest {

    private static final Instant CREATED_FROM = Instant.parse("2026-08-01T00:00:00Z");

    @Nested
    @DisplayName("정렬 정규화")
    class SortNormalization {

        @Test
        @DisplayName("정렬을 지정하지 않으면 승인된 기본 정렬을 적용한다")
        void appliesApprovedDefaultSortWhenOmitted() {
            TestSuiteListCriteria criteria = criteriaWithSort(List.of());

            assertEquals(
                    List.of(
                            SortOrder.desc(TestSuiteSortField.UPDATED_AT),
                            SortOrder.desc(TestSuiteSortField.ID)),
                    criteria.sort());
        }

        @Test
        @DisplayName("id 조건이 없으면 마지막 보조 정렬로 id desc를 덧붙인다")
        void appendsIdAsLastTieBreaker() {
            TestSuiteListCriteria criteria =
                    criteriaWithSort(List.of(SortOrder.asc(TestSuiteSortField.NAME)));

            assertEquals(
                    List.of(
                            SortOrder.asc(TestSuiteSortField.NAME),
                            SortOrder.desc(TestSuiteSortField.ID)),
                    criteria.sort());
        }

        @Test
        @DisplayName("id 조건이 이미 있으면 순서를 바꾸거나 덧붙이지 않는다")
        void keepsRequestedSortWhenIdAlreadyPresent() {
            List<SortOrder<TestSuiteSortField>> requested = List.of(
                    SortOrder.asc(TestSuiteSortField.ID),
                    SortOrder.asc(TestSuiteSortField.NAME));

            assertEquals(requested, criteriaWithSort(requested).sort());
        }

        @Test
        @DisplayName("요청 순서를 우선순위로 유지한다")
        void keepsRequestedOrderAsPriority() {
            TestSuiteListCriteria criteria = criteriaWithSort(List.of(
                    SortOrder.desc(TestSuiteSortField.TEST_CASE_COUNT),
                    SortOrder.asc(TestSuiteSortField.NAME)));

            assertEquals(TestSuiteSortField.TEST_CASE_COUNT, criteria.sort().get(0).field());
            assertEquals(TestSuiteSortField.NAME, criteria.sort().get(1).field());
        }
    }

    @Nested
    @DisplayName("조건 검증")
    class Validation {

        @Test
        @DisplayName("filter를 지정하지 않은 첫 페이지 조회를 만들 수 있다")
        void createsUnfilteredFirstPage() {
            TestSuiteListCriteria criteria = TestSuiteListCriteria.firstPage();

            assertEquals(1, criteria.page().number());
            assertEquals(PageCriteria.DEFAULT_SIZE, criteria.page().size());
        }

        @Test
        @DisplayName("이름 filter가 공백만 있으면 IllegalArgumentException을 던진다")
        void rejectsBlankNameFilter() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TestSuiteListCriteria(
                            "   ", null, null, null, null, List.of(), PageCriteria.firstPage()));
        }

        @Test
        @DisplayName("TestCase 개수 하한이 음수면 IllegalArgumentException을 던진다")
        void rejectsNegativeMinTestCaseCount() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new TestSuiteListCriteria(
                            null, null, null, -1L, null, List.of(), PageCriteria.firstPage()));
        }

        @Test
        @DisplayName("페이지 조건이 null이면 NullPointerException을 던진다")
        void rejectsNullPage() {
            assertThrows(
                    NullPointerException.class,
                    () -> new TestSuiteListCriteria(
                            null, null, null, null, null, List.of(), null));
        }

        @Test
        @DisplayName("정렬 목록이 null이면 NullPointerException을 던진다")
        void rejectsNullSort() {
            assertThrows(
                    NullPointerException.class,
                    () -> new TestSuiteListCriteria(
                            null, null, null, null, null, null, PageCriteria.firstPage()));
        }

        @Test
        @DisplayName("생성 시각 filter를 그대로 보유한다")
        void keepsCreatedInstantFilters() {
            TestSuiteListCriteria criteria = new TestSuiteListCriteria(
                    null, CREATED_FROM, null, null, null, List.of(), PageCriteria.firstPage());

            assertEquals(CREATED_FROM, criteria.createdFrom());
        }
    }

    private static TestSuiteListCriteria criteriaWithSort(
            List<SortOrder<TestSuiteSortField>> sort) {
        return new TestSuiteListCriteria(
                null, null, null, null, null, sort, PageCriteria.firstPage());
    }
}
