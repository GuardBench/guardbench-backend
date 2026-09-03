package com.guardbench.testdefinition.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testdefinition.application.query.PageCriteria;
import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.application.query.SortOrder;
import com.guardbench.testdefinition.application.query.TestSuiteListCriteria;
import com.guardbench.testdefinition.application.query.TestSuiteListQuery;
import com.guardbench.testdefinition.application.query.TestSuiteSortField;
import com.guardbench.testdefinition.application.query.TestSuiteSummary;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * TestSuite 목록 filter, 집계, 정렬과 Offset Pagination을 실제 PostgreSQL에서 검증한다.
 *
 * @see <a href="file:../docs/decisions/0002-postgresql-persistence-contract.md">ADR 0002</a>
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class TestSuiteListQueryAdapterIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-25T11:00:00Z");

    @Autowired
    private TestSuiteListQuery query;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("TestCase 수를 집계해 filter와 정렬한 뒤 페이지를 자른다")
    void filtersAndSortsByTestCaseCountBeforePagination() {
        insertSuite(10_001L, "빈 Suite", T0, T0);
        insertSuite(10_002L, "한 건 Suite", T0, T1);
        insertSuite(10_003L, "두 건 Suite", T1, T1);
        insertCase(20_001L, 10_002L, "첫 번째");
        insertCase(20_002L, 10_003L, "두 번째");
        insertCase(20_003L, 10_003L, "세 번째");

        TestSuiteListCriteria criteria = new TestSuiteListCriteria(
                null, null, null, 1L, 2L,
                List.of(SortOrder.desc(TestSuiteSortField.TEST_CASE_COUNT)),
                new PageCriteria(1, 1));

        PageResult<TestSuiteSummary> result = query.find(criteria);

        assertEquals(List.of(10_003L), result.items().stream().map(TestSuiteSummary::id).toList());
        assertEquals(2L, result.items().getFirst().testCaseCount());
        assertEquals(2L, result.totalElements());
        assertEquals(2, result.totalPages());
        assertTrue(result.hasNext());
    }

    @Test
    @DisplayName("생성 시각 범위는 시작을 포함하고 끝을 제외하며 초과 페이지는 빈 목록이다")
    void appliesHalfOpenCreatedRangeAndReturnsEmptyOutOfRangePage() {
        insertSuite(10_011L, "이전", T0.minusSeconds(1), T0);
        insertSuite(10_012L, "범위 안", T0, T0);
        insertSuite(10_013L, "끝 경계", T1, T1);

        TestSuiteListCriteria criteria = new TestSuiteListCriteria(
                null, T0, T1, null, null, List.of(), new PageCriteria(2, 1));

        PageResult<TestSuiteSummary> result = query.find(criteria);

        assertEquals(List.of(), result.items());
        assertEquals(1L, result.totalElements());
        assertEquals(2, result.number());
    }

    @Test
    @DisplayName("이름 부분 검색의 대소문자를 무시하고 percent와 underscore를 문자로 취급한다")
    void treatsLikeMetacharactersAsLiteralCharacters() {
        insertSuite(10_021L, "Rate_100% CHECK", T0, T0);
        insertSuite(10_022L, "RateX1000 CHECK", T0, T0);

        TestSuiteListCriteria criteria = new TestSuiteListCriteria(
                "_100% check", null, null, null, null, List.of(), PageCriteria.firstPage());

        PageResult<TestSuiteSummary> result = query.find(criteria);

        assertEquals(List.of(10_021L), result.items().stream().map(TestSuiteSummary::id).toList());
        assertEquals(1L, result.totalElements());
    }

    private void insertSuite(long id, String name, Instant createdAt, Instant updatedAt) {
        jdbcTemplate.update("""
                INSERT INTO test_suite (id, name, description, created_at, updated_at)
                VALUES (?, ?, NULL, ?, ?)
                """, id, name, Timestamp.from(createdAt), Timestamp.from(updatedAt));
    }

    private void insertCase(long id, long suiteId, String name) {
        jdbcTemplate.update("""
                INSERT INTO test_case (
                    id, test_suite_id, name, input, expected_action, severity, category,
                    created_at, updated_at)
                VALUES (?, ?, ?, 'input', 'BLOCK', 'LOW', 'PII', ?, ?)
                """, id, suiteId, name, Timestamp.from(T0), Timestamp.from(T0));
    }
}
