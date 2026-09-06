package com.guardbench.testrun.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.guardbench.testrun.application.port.out.LoadTestRunListPort;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.QualityGateMetricView;
import com.guardbench.testrun.application.port.out.QualityGateMetricsView;
import com.guardbench.testrun.application.port.out.SortDirection;
import com.guardbench.testrun.application.port.out.SortOrder;
import com.guardbench.testrun.application.port.out.TestRunListCriteria;
import com.guardbench.testrun.application.port.out.TestRunListItem;
import com.guardbench.testrun.application.port.out.TestRunListSortField;
import com.guardbench.testrun.application.port.out.TestRunProgress;
import com.guardbench.testrun.domain.TestRunExecutionOutcome;
import com.guardbench.testrun.domain.TestRunStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * TestRun 목록 조회 Port를 PostgreSQL query로 구현한다.
 *
 * <p>Quality Gate 상태와 metric evidence는 {@code quality_gate_result}를 LEFT JOIN해서 읽고,
 * evaluation Context의 Domain 타입이나 Repository를 사용하지 않는다. 저장된 원시 값을 이 Context가
 * 소유한 nullable 조회 projection으로만 전달한다.
 *
 * <p>진행률은 {@code processed_test_case_count}와 {@code test_case_count}로 계산한다. 정렬 SQL은
 * 문자열 입력이 아니라 {@link TestRunListSortField}의 고정 mapping으로만 조립한다.
 *
 * @see <a href="../../../../../../../docs/api/openapi.yaml">GuardBench API V1</a>
 * @see <a href="../../../../../../../docs/decisions/0002-postgresql-persistence-contract.md">ADR 0002</a>
 */
@Repository
@Transactional(readOnly = true)
class TestRunListPersistenceAdapter implements LoadTestRunListPort {

    private final JdbcTemplate jdbcTemplate;

    TestRunListPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResult<TestRunListItem> load(TestRunListCriteria criteria) {
        Objects.requireNonNull(criteria, "TestRunListCriteria must not be null");

        QueryParts query = queryParts(criteria);
        List<Object> pageArguments = new ArrayList<>(query.arguments());
        pageArguments.add(criteria.page().size());
        pageArguments.add(criteria.page().offset());

        String fromAndWhere = """
                FROM test_run r
                LEFT JOIN quality_gate_result qgr ON qgr.test_run_id = r.id
                """ + query.whereClause();
        String selectSql = """
                SELECT r.id, r.test_suite_id, r.status, r.test_case_count,
                       r.processed_test_case_count, r.execution_outcome,
                       qgr.gate_status,
                       qgr.assertion_pass_rate, qgr.assertion_pass_rate_threshold, qgr.assertion_passed,
                       qgr.execution_success_rate, qgr.execution_success_rate_threshold, qgr.execution_passed,
                       r.created_at, r.started_at, r.completed_at,
                       r.updated_at
                """ + fromAndWhere
                + " ORDER BY " + orderBy(criteria.sort())
                + " LIMIT ? OFFSET ?";
        String countSql = "SELECT COUNT(*)\n" + fromAndWhere;

        List<TestRunListItem> items = jdbcTemplate.query(
                selectSql, this::mapItem, pageArguments.toArray());
        long totalElements = jdbcTemplate.queryForObject(
                countSql, Long.class, query.arguments().toArray());

        return PageResult.of(items, criteria.page(), totalElements);
    }

    private QueryParts queryParts(TestRunListCriteria criteria) {
        List<String> predicates = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();

        if (criteria.testSuiteId() != null) {
            predicates.add("r.test_suite_id = ?");
            arguments.add(criteria.testSuiteId());
        }
        addInClause(predicates, arguments, "r.status",
                criteria.statuses().stream().map(TestRunStatus::name).toList());
        addInClause(predicates, arguments, "r.execution_outcome",
                criteria.executionOutcomes().stream().map(TestRunExecutionOutcome::name).toList());
        addInClause(predicates, arguments, "qgr.gate_status", List.copyOf(criteria.qualityGateStatusCodes()));
        if (criteria.createdFrom() != null) {
            predicates.add("r.created_at >= ?");
            arguments.add(Timestamp.from(criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            predicates.add("r.created_at < ?");
            arguments.add(Timestamp.from(criteria.createdTo()));
        }

        String whereClause = predicates.isEmpty()
                ? ""
                : "WHERE " + String.join(" AND ", predicates) + "\n";
        return new QueryParts(whereClause, List.copyOf(arguments));
    }

    private String orderBy(List<SortOrder<TestRunListSortField>> sort) {
        return sort.stream()
                .map(order -> switch (order.field()) {
                    case CREATED_AT -> directed("r.created_at", order.direction());
                    case STARTED_AT -> directedNullsLast("r.started_at", order.direction());
                    case COMPLETED_AT -> directedNullsLast("r.completed_at", order.direction());
                    case UPDATED_AT -> directed("r.updated_at", order.direction());
                    case TEST_CASE_COUNT -> directed("r.test_case_count", order.direction());
                    case ID -> directed("r.id", order.direction());
                })
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
    }

    private TestRunListItem mapItem(ResultSet resultSet, int rowNumber) throws SQLException {
        String executionOutcome = resultSet.getString("execution_outcome");
        String qualityGateStatus = resultSet.getString("gate_status");
        return new TestRunListItem(
                resultSet.getLong("id"),
                resultSet.getLong("test_suite_id"),
                TestRunStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("test_case_count"),
                new TestRunProgress(
                        resultSet.getInt("processed_test_case_count"),
                        percent(resultSet.getInt("processed_test_case_count"), resultSet.getInt("test_case_count"))),
                executionOutcome == null ? null : TestRunExecutionOutcome.valueOf(executionOutcome),
                qualityGateStatus,
                mapQualityGateMetrics(resultSet, qualityGateStatus),
                toInstant(resultSet, "created_at"),
                toInstant(resultSet, "started_at"),
                toInstant(resultSet, "completed_at"),
                toInstant(resultSet, "updated_at"));
    }

    private QualityGateMetricsView mapQualityGateMetrics(ResultSet resultSet, String qualityGateStatus)
            throws SQLException {
        if (qualityGateStatus == null || "NOT_EVALUATED".equals(qualityGateStatus)) {
            return null;
        }
        return new QualityGateMetricsView(
                new QualityGateMetricView(
                        requiredDouble(resultSet, "assertion_pass_rate"),
                        requiredDouble(resultSet, "assertion_pass_rate_threshold"),
                        requiredBoolean(resultSet, "assertion_passed")),
                new QualityGateMetricView(
                        requiredDouble(resultSet, "execution_success_rate"),
                        requiredDouble(resultSet, "execution_success_rate_threshold"),
                        requiredBoolean(resultSet, "execution_passed")));
    }

    private static double requiredDouble(ResultSet resultSet, String column) throws SQLException {
        Double value = resultSet.getObject(column, Double.class);
        if (value == null) {
            throw new SQLException("Missing required Quality Gate metric column: " + column);
        }
        return value;
    }

    private static boolean requiredBoolean(ResultSet resultSet, String column) throws SQLException {
        Boolean value = resultSet.getObject(column, Boolean.class);
        if (value == null) {
            throw new SQLException("Missing required Quality Gate metric column: " + column);
        }
        return value;
    }

    private static double percent(int processed, int total) {
        return total == 0 ? 0.0 : (processed * 100.0) / total;
    }

    private static java.time.Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static void addInClause(
            List<String> predicates, List<Object> arguments, String column, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        String placeholders = String.join(", ", values.stream().map(v -> "?").toList());
        predicates.add(column + " IN (" + placeholders + ")");
        arguments.addAll(values);
    }

    private static String directed(String expression, SortDirection direction) {
        return expression + (direction == SortDirection.ASC ? " ASC" : " DESC");
    }

    private static String directedNullsLast(String expression, SortDirection direction) {
        return expression + (direction == SortDirection.ASC ? " ASC" : " DESC") + " NULLS LAST";
    }

    private record QueryParts(String whereClause, List<Object> arguments) {
    }
}
