package com.guardbench.testdefinition.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.application.query.SortDirection;
import com.guardbench.testdefinition.application.query.SortOrder;
import com.guardbench.testdefinition.application.query.TestSuiteListCriteria;
import com.guardbench.testdefinition.application.query.TestSuiteListQuery;
import com.guardbench.testdefinition.application.query.TestSuiteSortField;
import com.guardbench.testdefinition.application.query.TestSuiteSummary;

/**
 * TestSuite 목록 조회 Port를 PostgreSQL query로 구현한다.
 *
 * <p>TestCase 수는 중복 저장하지 않고 상관 subquery로 집계한다. 같은 집계식을 Projection과
 * count filter 양쪽에서 사용하고, 전체 filter와 정렬이 끝난 뒤 LIMIT/OFFSET을 적용한다.
 *
 * <p>정렬 SQL은 문자열 입력이 아니라 {@link TestSuiteSortField}의 고정 mapping으로만 조립한다.
 *
 * <p>근거: {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
@Repository
@Transactional(readOnly = true)
class TestSuiteListQueryAdapter implements TestSuiteListQuery {

    private static final String ACTIVE_TEST_CASE_COUNT = """
            (SELECT COUNT(*)
               FROM test_case tc
              WHERE tc.test_suite_id = s.id
                )
            """;

    private final JdbcTemplate jdbcTemplate;

    TestSuiteListQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResult<TestSuiteSummary> find(TestSuiteListCriteria criteria) {
        Objects.requireNonNull(criteria, "TestSuiteListCriteria must not be null");

        QueryParts query = queryParts(criteria);
        List<Object> pageArguments = new ArrayList<>(query.arguments());
        pageArguments.add(criteria.page().size());
        pageArguments.add(criteria.page().offset());

        String selectSql = """
                SELECT s.id, s.name, s.description, s.created_at, s.updated_at,
                """ + ACTIVE_TEST_CASE_COUNT + " AS test_case_count\n"
                + "FROM test_suite s\n"
                + query.whereClause()
                + " ORDER BY " + orderBy(criteria.sort())
                + " LIMIT ? OFFSET ?";
        String countSql = "SELECT COUNT(*) FROM test_suite s\n" + query.whereClause();

        List<TestSuiteSummary> items = jdbcTemplate.query(
                selectSql, this::mapSummary, pageArguments.toArray());
        long totalElements = jdbcTemplate.queryForObject(
                countSql, Long.class, query.arguments().toArray());

        return PageResult.of(items, criteria.page(), totalElements);
    }

    private QueryParts queryParts(TestSuiteListCriteria criteria) {
        List<String> predicates = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();

        if (criteria.nameContains() != null) {
            predicates.add("LOWER(s.name) LIKE LOWER(?) ESCAPE '\\'");
            arguments.add(containsPattern(criteria.nameContains()));
        }
        if (criteria.createdFrom() != null) {
            predicates.add("s.created_at >= ?");
            arguments.add(Timestamp.from(criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            predicates.add("s.created_at < ?");
            arguments.add(Timestamp.from(criteria.createdTo()));
        }
        if (criteria.minTestCaseCount() != null) {
            predicates.add(ACTIVE_TEST_CASE_COUNT + " >= ?");
            arguments.add(criteria.minTestCaseCount());
        }
        if (criteria.maxTestCaseCount() != null) {
            predicates.add(ACTIVE_TEST_CASE_COUNT + " <= ?");
            arguments.add(criteria.maxTestCaseCount());
        }

        String whereClause = predicates.isEmpty()
                ? ""
                : "WHERE " + String.join(" AND ", predicates) + "\n";

        return new QueryParts(whereClause, List.copyOf(arguments));
    }

    private String orderBy(List<SortOrder<TestSuiteSortField>> sort) {
        return sort.stream()
                .map(order -> switch (order.field()) {
                    case NAME -> directed("s.name", order.direction());
                    case CREATED_AT -> directed("s.created_at", order.direction());
                    case UPDATED_AT -> directed("s.updated_at", order.direction());
                    case TEST_CASE_COUNT -> directed("test_case_count", order.direction());
                    case ID -> directed("s.id", order.direction());
                })
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
    }

    private TestSuiteSummary mapSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TestSuiteSummary(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getLong("test_case_count"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static String directed(String expression, SortDirection direction) {
        return expression + (direction == SortDirection.ASC ? " ASC" : " DESC");
    }

    private static String containsPattern(String value) {
        return "%" + value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_") + "%";
    }

    private record QueryParts(String whereClause, List<Object> arguments) {
    }
}
