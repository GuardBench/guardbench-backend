package com.guardbench.testrun.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.guardbench.testrun.application.port.out.LoadTestRunResultListPort;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.SortDirection;
import com.guardbench.testrun.application.port.out.SortOrder;
import com.guardbench.testrun.application.port.out.TestExecutionView;
import com.guardbench.testrun.application.port.out.TestRunResultItem;
import com.guardbench.testrun.application.port.out.TestRunResultListCriteria;
import com.guardbench.testrun.application.port.out.TestRunResultSortField;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.TestExecutionStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * TestRun 개별 결과 목록 조회 Port를 PostgreSQL query로 구현한다. Snapshot별 Baseline/Candidate
 * {@code test_execution}, {@code assertion_result}, {@code change_result}를 조합한다.
 *
 * <p>{@code test_execution}은 Snapshot당 Baseline·Candidate 두 행이므로 같은 테이블을 target_type별로
 * 두 번 JOIN한다. Assertion·Change 결과는 evaluation Context Domain 타입을 사용하지 않고 이 Context가
 * 소유한 nullable scalar code로만 옮긴다.
 *
 * <p>Severity는 저장 code의 사전순 대신 승인된 LOW, MEDIUM, HIGH, CRITICAL 의미 순서로 정렬한다.
 *
 * @see <a href="../../../../../../../docs/api/openapi.yaml">GuardBench API V1</a>
 * @see <a href="../../../../../../../docs/decisions/0002-postgresql-persistence-contract.md">ADR 0002</a>
 */
@Repository
@Transactional(readOnly = true)
class TestRunResultListPersistenceAdapter implements LoadTestRunResultListPort {

    private static final String SEVERITY_ORDER = """
            CASE s.severity
                WHEN 'LOW' THEN 0
                WHEN 'MEDIUM' THEN 1
                WHEN 'HIGH' THEN 2
                WHEN 'CRITICAL' THEN 3
            END
            """;

    private final JdbcTemplate jdbcTemplate;

    TestRunResultListPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PageResult<TestRunResultItem> load(long testRunId, TestRunResultListCriteria criteria) {
        Objects.requireNonNull(criteria, "TestRunResultListCriteria must not be null");

        QueryParts query = queryParts(testRunId, criteria);
        List<Object> pageArguments = new ArrayList<>(query.arguments());
        pageArguments.add(criteria.page().size());
        pageArguments.add(criteria.page().offset());

        String fromAndWhere = """
                FROM test_case_snapshot s
                JOIN test_execution be ON be.snapshot_id = s.id AND be.target_type = 'BASELINE'
                JOIN test_execution ce ON ce.snapshot_id = s.id AND ce.target_type = 'CANDIDATE'
                LEFT JOIN assertion_result ar ON ar.snapshot_id = s.id
                LEFT JOIN change_result cr ON cr.snapshot_id = s.id
                """ + query.whereClause();
        String selectSql = """
                SELECT s.id AS snapshot_id, s.source_test_case_id, s.name, s.input,
                       s.expected_action, s.severity, s.category,
                       be.result_status AS baseline_status, be.actual_action AS baseline_action,
                       be.error_code AS baseline_error_code, be.error_message AS baseline_error_message,
                       ce.result_status AS candidate_status, ce.actual_action AS candidate_action,
                       ce.error_code AS candidate_error_code, ce.error_message AS candidate_error_message,
                       ar.assertion_status, cr.comparability_status, cr.change_type
                """ + fromAndWhere
                + " ORDER BY " + orderBy(criteria.sort())
                + " LIMIT ? OFFSET ?";
        String countSql = "SELECT COUNT(*)\n" + fromAndWhere;

        List<TestRunResultItem> items = jdbcTemplate.query(
                selectSql, this::mapItem, pageArguments.toArray());
        long totalElements = jdbcTemplate.queryForObject(
                countSql, Long.class, query.arguments().toArray());

        return PageResult.of(items, criteria.page(), totalElements);
    }

    private QueryParts queryParts(long testRunId, TestRunResultListCriteria criteria) {
        List<String> predicates = new ArrayList<>();
        List<Object> arguments = new ArrayList<>();

        predicates.add("s.test_run_id = ?");
        arguments.add(testRunId);

        addContains(predicates, arguments, "s.name", criteria.nameContains());
        addContains(predicates, arguments, "s.input", criteria.inputContains());
        addEquals(predicates, arguments, "s.category", criteria.category());
        addEquals(predicates, arguments, "s.expected_action",
                criteria.expectedAction() == null ? null : criteria.expectedAction().name());
        addEquals(predicates, arguments, "s.severity",
                criteria.severity() == null ? null : criteria.severity().name());
        addEquals(predicates, arguments, "be.result_status",
                criteria.baselineExecutionStatus() == null ? null : criteria.baselineExecutionStatus().name());
        addEquals(predicates, arguments, "ce.result_status",
                criteria.candidateExecutionStatus() == null ? null : criteria.candidateExecutionStatus().name());
        addEquals(predicates, arguments, "ar.assertion_status", criteria.assertionStatusCode());
        addEquals(predicates, arguments, "cr.comparability_status", criteria.comparabilityStatusCode());
        addEquals(predicates, arguments, "cr.change_type", criteria.changeTypeCode());

        return new QueryParts(
                "WHERE " + String.join(" AND ", predicates) + "\n",
                List.copyOf(arguments));
    }

    private String orderBy(List<SortOrder<TestRunResultSortField>> sort) {
        return sort.stream()
                .map(order -> switch (order.field()) {
                    case NAME -> directed("s.name", order.direction());
                    case CATEGORY -> directed("s.category", order.direction());
                    case EXPECTED_ACTION -> directed("s.expected_action", order.direction());
                    case SEVERITY -> directed(SEVERITY_ORDER, order.direction());
                    case SNAPSHOT_ID -> directed("s.id", order.direction());
                })
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
    }

    private TestRunResultItem mapItem(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TestRunResultItem(
                resultSet.getLong("snapshot_id"),
                resultSet.getLong("source_test_case_id"),
                resultSet.getString("name"),
                resultSet.getString("input"),
                Action.valueOf(resultSet.getString("expected_action")),
                Severity.valueOf(resultSet.getString("severity")),
                resultSet.getString("category"),
                mapExecution(resultSet, "baseline_status", "baseline_action",
                        "baseline_error_code", "baseline_error_message"),
                mapExecution(resultSet, "candidate_status", "candidate_action",
                        "candidate_error_code", "candidate_error_message"),
                resultSet.getString("assertion_status"),
                resultSet.getString("comparability_status"),
                resultSet.getString("change_type"));
    }

    private TestExecutionView mapExecution(
            ResultSet resultSet, String statusColumn, String actionColumn,
            String errorCodeColumn, String errorMessageColumn) throws SQLException {
        String actualAction = resultSet.getString(actionColumn);
        return new TestExecutionView(
                TestExecutionStatus.valueOf(resultSet.getString(statusColumn)),
                actualAction == null ? null : Action.valueOf(actualAction),
                resultSet.getString(errorCodeColumn),
                resultSet.getString(errorMessageColumn));
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
