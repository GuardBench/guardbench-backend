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
import com.guardbench.testdefinition.application.query.TestCaseListCriteria;
import com.guardbench.testdefinition.application.query.TestCaseListQuery;
import com.guardbench.testdefinition.application.query.TestCaseSortField;
import com.guardbench.testdefinition.application.query.TestCaseSummary;
import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;

/**
 * 활성 TestCase 목록 조회 Port를 PostgreSQL query로 구현한다.
 *
 * <p>논리 삭제 조건은 호출자가 선택하는 filter가 아니라 모든 select와 count query에 항상 적용한다.
 * Severity는 저장 code의 사전순 대신 승인된 LOW, MEDIUM, HIGH, CRITICAL 의미 순서로 정렬한다.
 *
 * <p>근거: {@code docs/api/openapi.yaml},
 * {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
@Repository
@Transactional(readOnly = true)
class TestCaseListQueryAdapter implements TestCaseListQuery {

    private static final String SEVERITY_ORDER = """
            CASE tc.severity
                WHEN 'LOW' THEN 0
                WHEN 'MEDIUM' THEN 1
                WHEN 'HIGH' THEN 2
                WHEN 'CRITICAL' THEN 3
            END
            """;

    private final JdbcTemplate jdbcTemplate;

    TestCaseListQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResult<TestCaseSummary> findActive(TestCaseListCriteria criteria) {
        Objects.requireNonNull(criteria, "TestCaseListCriteria must not be null");

        QueryParts query = queryParts(criteria);
        List<Object> pageArguments = new ArrayList<>(query.arguments());
        pageArguments.add(criteria.page().size());
        pageArguments.add(criteria.page().offset());

        String fromAndWhere = "FROM test_case tc\n" + query.whereClause();
        String selectSql = """
                SELECT tc.id, tc.name, tc.input, tc.expected_action, tc.severity, tc.category,
                       tc.created_at, tc.updated_at
                """ + fromAndWhere
                + " ORDER BY " + orderBy(criteria.sort())
                + " LIMIT ? OFFSET ?";
        String countSql = "SELECT COUNT(*)\n" + fromAndWhere;

        List<TestCaseSummary> items = jdbcTemplate.query(
                selectSql, this::mapSummary, pageArguments.toArray());
        long totalElements = jdbcTemplate.queryForObject(
                countSql, Long.class, query.arguments().toArray());

        return PageResult.of(items, criteria.page(), totalElements);
    }

    private QueryParts queryParts(TestCaseListCriteria criteria) {
        List<String> predicates = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();

        predicates.add("tc.test_suite_id = ?");
        arguments.add(criteria.testSuiteId().value());
        predicates.add("tc.deleted_at IS NULL");

        addContains(predicates, arguments, "tc.name", criteria.nameContains());
        addContains(predicates, arguments, "tc.input", criteria.inputContains());
        addEquals(predicates, arguments, "tc.category", criteria.category());
        addEquals(predicates, arguments, "tc.expected_action",
                criteria.expectedAction() == null ? null : criteria.expectedAction().name());
        addEquals(predicates, arguments, "tc.severity",
                criteria.severity() == null ? null : criteria.severity().name());
        if (criteria.createdFrom() != null) {
            predicates.add("tc.created_at >= ?");
            arguments.add(Timestamp.from(criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            predicates.add("tc.created_at < ?");
            arguments.add(Timestamp.from(criteria.createdTo()));
        }

        return new QueryParts(
                "WHERE " + String.join(" AND ", predicates) + "\n",
                List.copyOf(arguments));
    }

    private String orderBy(List<SortOrder<TestCaseSortField>> sort) {
        return sort.stream()
                .map(order -> switch (order.field()) {
                    case NAME -> directed("tc.name", order.direction());
                    case CATEGORY -> directed("tc.category", order.direction());
                    case EXPECTED_ACTION -> directed("tc.expected_action", order.direction());
                    case SEVERITY -> directed(SEVERITY_ORDER, order.direction());
                    case CREATED_AT -> directed("tc.created_at", order.direction());
                    case UPDATED_AT -> directed("tc.updated_at", order.direction());
                    case ID -> directed("tc.id", order.direction());
                })
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
    }

    private TestCaseSummary mapSummary(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TestCaseSummary(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("input"),
                Action.valueOf(resultSet.getString("expected_action")),
                Severity.valueOf(resultSet.getString("severity")),
                resultSet.getString("category"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant());
    }

    private static void addContains(
            List<String> predicates, List<Object> arguments, String column, String value) {
        if (value != null) {
            predicates.add("LOWER(" + column + ") LIKE LOWER(?) ESCAPE '\\'");
            arguments.add(containsPattern(value));
        }
    }

    private static void addEquals(
            List<String> predicates, List<Object> arguments, String column, Object value) {
        if (value != null) {
            predicates.add(column + " = ?");
            arguments.add(value);
        }
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
