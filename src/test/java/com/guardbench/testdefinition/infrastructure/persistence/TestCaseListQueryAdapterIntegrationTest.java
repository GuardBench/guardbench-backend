package com.guardbench.testdefinition.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
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
import com.guardbench.testdefinition.application.query.TestCaseListCriteria;
import com.guardbench.testdefinition.application.query.TestCaseListQuery;
import com.guardbench.testdefinition.application.query.TestCaseSortField;
import com.guardbench.testdefinition.application.query.TestCaseSummary;
import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testdefinition.domain.TestSuiteId;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * 활성 TestCase 목록 filter, 의미 정렬과 Offset Pagination을 실제 PostgreSQL에서 검증한다.
 *
 * @see <a href="file:../docs/decisions/0002-postgresql-persistence-contract.md">ADR 0002</a>
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class TestCaseListQueryAdapterIntegrationTest {

    private static final long SUITE_ID = 11_001L;
    private static final Instant T0 = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-25T11:00:00Z");

    @Autowired
    private TestCaseListQuery query;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void insertOwningSuite() {
        jdbcTemplate.update("""
                INSERT INTO test_suite (id, name, created_at, updated_at)
                VALUES (?, '조회 Suite', ?, ?)
                """, SUITE_ID, Timestamp.from(T0), Timestamp.from(T0));
    }

    @Test
    @DisplayName("severity는 LOW부터 CRITICAL 의미 순서로 정렬하고 삭제 행은 전체 건수에서도 제외한다")
    void sortsSeverityByMeaningAndExcludesDeletedRows() {
        insertCase(21_001L, "critical", "input", Action.BLOCK, Severity.CRITICAL, "PII", T0, null);
        insertCase(21_002L, "low", "input", Action.BLOCK, Severity.LOW, "PII", T0, null);
        insertCase(21_003L, "high", "input", Action.BLOCK, Severity.HIGH, "PII", T0, null);
        insertCase(21_004L, "medium", "input", Action.BLOCK, Severity.MEDIUM, "PII", T0, null);
        insertCase(21_005L, "deleted", "input", Action.BLOCK, Severity.LOW, "PII", T0, T1);

        TestCaseListCriteria criteria = new TestCaseListCriteria(
                new TestSuiteId(SUITE_ID), null, null, null, null, null, null, null,
                List.of(SortOrder.asc(TestCaseSortField.SEVERITY)), new PageCriteria(1, 3));

        PageResult<TestCaseSummary> result = query.findActive(criteria);

        assertEquals(
                List.of(Severity.LOW, Severity.MEDIUM, Severity.HIGH),
                result.items().stream().map(TestCaseSummary::severity).toList());
        assertEquals(4L, result.totalElements());
        assertTrue(result.hasNext());
    }

    @Test
    @DisplayName("모든 TestCase filter를 AND로 결합하고 생성 끝 시각은 제외한다")
    void combinesAllFiltersWithHalfOpenCreatedRange() {
        insertCase(21_011L, "PII_100% 차단", "SECRET token", Action.BLOCK,
                Severity.CRITICAL, "PII", T0, null);
        insertCase(21_012L, "PII_100% 허용", "secret token", Action.ALLOW,
                Severity.CRITICAL, "PII", T0, null);
        insertCase(21_013L, "PII_100% 차단", "secret token", Action.BLOCK,
                Severity.CRITICAL, "PII", T1, null);

        TestCaseListCriteria criteria = new TestCaseListCriteria(
                new TestSuiteId(SUITE_ID), "_100% 차단", "secret", "PII",
                Action.BLOCK, Severity.CRITICAL, T0, T1,
                List.of(SortOrder.asc(TestCaseSortField.NAME)), PageCriteria.firstPage());

        PageResult<TestCaseSummary> result = query.findActive(criteria);

        assertEquals(List.of(21_011L), result.items().stream().map(TestCaseSummary::id).toList());
        assertEquals(1L, result.totalElements());
    }

    @Test
    @DisplayName("같은 정렬 값에는 id 보조 정렬을 적용해 연속 페이지가 중복되지 않는다")
    void appliesStableIdentifierSortAcrossPages() {
        insertCase(21_021L, "same", "input", Action.ALLOW, Severity.LOW, "A", T0, null);
        insertCase(21_022L, "same", "input", Action.ALLOW, Severity.LOW, "A", T0, null);
        insertCase(21_023L, "same", "input", Action.ALLOW, Severity.LOW, "A", T0, null);

        PageResult<TestCaseSummary> first = query.findActive(criteriaForPage(1));
        PageResult<TestCaseSummary> second = query.findActive(criteriaForPage(2));

        assertEquals(List.of(21_021L, 21_022L), first.items().stream().map(TestCaseSummary::id).toList());
        assertEquals(List.of(21_023L), second.items().stream().map(TestCaseSummary::id).toList());
        assertEquals(3L, second.totalElements());
    }

    private TestCaseListCriteria criteriaForPage(int number) {
        return new TestCaseListCriteria(
                new TestSuiteId(SUITE_ID), null, null, null, null, null, null, null,
                List.of(SortOrder.asc(TestCaseSortField.NAME)), new PageCriteria(number, 2));
    }

    private void insertCase(
            long id,
            String name,
            String input,
            Action action,
            Severity severity,
            String category,
            Instant createdAt,
            Instant deletedAt) {
        jdbcTemplate.update("""
                INSERT INTO test_case (
                    id, test_suite_id, name, input, expected_action, severity, category,
                    created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, id, SUITE_ID, name, input, action.name(), severity.name(), category,
                Timestamp.from(createdAt), Timestamp.from(deletedAt == null ? createdAt : deletedAt),
                deletedAt == null ? null : Timestamp.from(deletedAt));
    }
}
