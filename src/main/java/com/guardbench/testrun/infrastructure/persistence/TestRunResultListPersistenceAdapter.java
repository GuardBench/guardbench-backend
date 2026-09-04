package com.guardbench.testrun.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.guardbench.testrun.application.port.out.LoadTestRunResultListPort;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.SortDirection;
import com.guardbench.testrun.application.port.out.TestExecutionView;
import com.guardbench.testrun.application.port.out.TestRunResultAttentionFacets;
import com.guardbench.testrun.application.port.out.TestRunResultAttentionType;
import com.guardbench.testrun.application.port.out.TestRunResultItem;
import com.guardbench.testrun.application.port.out.TestRunResultListCriteria;
import com.guardbench.testrun.application.port.out.TestRunResultListView;
import com.guardbench.testrun.application.port.out.TestRunResultSortField;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.TestExecutionStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * TestRun 개별 결과 목록 조회 Port를 PostgreSQL query로 구현한다. Snapshot별 단일
 * {@code test_execution}과 {@code assertion_result}를 조합한다.
 *
 * <p>{@code test_execution}은 Snapshot당 단일 행이며 {@code snapshot_id}로 JOIN한다. Assertion 결과는 evaluation Context Domain 타입을 사용하지 않고 이 Context가
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
    private static final String FALSE_NEGATIVE_PREDICATE =
            "e.result_status = 'SUCCEEDED' AND s.expected_action = 'BLOCK' AND e.evaluator_verdict = 'ALLOW'";
    private static final String FALSE_POSITIVE_PREDICATE =
            "e.result_status = 'SUCCEEDED' AND s.expected_action = 'ALLOW' AND e.evaluator_verdict = 'BLOCK'";
    private static final String ATTENTION_TYPE_ORDER = """
            CASE
                WHEN %s THEN 0
                WHEN e.result_status = 'FAILED' THEN 1
                WHEN e.result_status = 'TIMED_OUT' THEN 2
                WHEN %s THEN 3
                WHEN e.result_status = 'NOT_STARTED' THEN 4
                ELSE 5
            END
            """.formatted(FALSE_NEGATIVE_PREDICATE, FALSE_POSITIVE_PREDICATE);

    private final JdbcTemplate jdbcTemplate;

    TestRunResultListPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TestRunResultListView load(long testRunId, TestRunResultListCriteria criteria) {
        Objects.requireNonNull(criteria, "TestRunResultListCriteria must not be null");

        QueryParts query = queryParts(testRunId, criteria, true);
        List<Object> pageArguments = new ArrayList<>(query.arguments());
        pageArguments.add(criteria.page().size());
        pageArguments.add(criteria.page().offset());

        String fromAndWhere = """
                FROM test_case_snapshot s
                JOIN test_execution e ON e.snapshot_id = s.id
                LEFT JOIN assertion_result ar ON ar.snapshot_id = s.id
                """ + query.whereClause();
        String selectSql = """
                SELECT s.id AS snapshot_id, s.source_test_case_id, s.name, s.input,
                       s.expected_action, s.severity, s.category,
                       e.result_status AS execution_status,
                       e.evaluator_verdict,
                       e.error_stage, e.error_code, e.error_message, ar.assertion_status
                """ + fromAndWhere
                + " ORDER BY " + orderBy(criteria)
                + " LIMIT ? OFFSET ?";
        String countSql = "SELECT COUNT(*)\n" + fromAndWhere;

        List<TestRunResultItem> items = jdbcTemplate.query(
                selectSql, this::mapItem, pageArguments.toArray());
        long totalElements = jdbcTemplate.queryForObject(
                countSql, Long.class, query.arguments().toArray());

        TestRunResultAttentionFacets facets = criteria.includeAttentionFacets()
                ? loadAttentionFacets(testRunId, criteria) : null;
        return new TestRunResultListView(
                PageResult.of(items, criteria.page(), totalElements), facets);
    }

    private QueryParts queryParts(
            long testRunId, TestRunResultListCriteria criteria, boolean includeAttentionFilter) {
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
        addEquals(predicates, arguments, "e.result_status",
                criteria.executionStatus() == null ? null : criteria.executionStatus().name());
        addEquals(predicates, arguments, "ar.assertion_status", criteria.assertionStatusCode());
        addEvaluationOutcome(predicates, arguments, criteria.evaluationOutcomeCode());
        if (includeAttentionFilter) {
            addAttentionTypes(predicates, criteria.attentionTypes());
        }

        return new QueryParts(
                "WHERE " + String.join(" AND ", predicates) + "\n",
                List.copyOf(arguments));
    }

    private TestRunResultAttentionFacets loadAttentionFacets(
            long testRunId, TestRunResultListCriteria criteria) {
        QueryParts query = queryParts(testRunId, criteria, false);
        String sql = """
                SELECT COUNT(*) AS all_results,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0) AS false_negative,
                       COALESCE(SUM(CASE WHEN %s THEN 1 ELSE 0 END), 0) AS false_positive,
                       COALESCE(SUM(CASE WHEN e.result_status = 'FAILED' THEN 1 ELSE 0 END), 0) AS execution_failed,
                       COALESCE(SUM(CASE WHEN e.result_status = 'TIMED_OUT' THEN 1 ELSE 0 END), 0) AS timed_out,
                       COALESCE(SUM(CASE WHEN e.result_status = 'NOT_STARTED' THEN 1 ELSE 0 END), 0) AS not_started
                FROM test_case_snapshot s
                JOIN test_execution e ON e.snapshot_id = s.id
                LEFT JOIN assertion_result ar ON ar.snapshot_id = s.id
                """.formatted(FALSE_NEGATIVE_PREDICATE, FALSE_POSITIVE_PREDICATE) + query.whereClause();
        return jdbcTemplate.queryForObject(sql, (resultSet, rowNumber) -> new TestRunResultAttentionFacets(
                resultSet.getLong("all_results"),
                resultSet.getLong("false_negative"),
                resultSet.getLong("false_positive"),
                resultSet.getLong("execution_failed"),
                resultSet.getLong("timed_out"),
                resultSet.getLong("not_started")), query.arguments().toArray());
    }

    private static void addEvaluationOutcome(
            List<String> predicates, List<Object> arguments, String outcome) {
        if (outcome == null) {
            return;
        }
        predicates.add("(" + switch (outcome) {
            case "TRUE_POSITIVE" -> "s.expected_action = 'BLOCK' AND e.evaluator_verdict = 'BLOCK'";
            case "TRUE_NEGATIVE" -> "s.expected_action = 'ALLOW' AND e.evaluator_verdict = 'ALLOW'";
            case "FALSE_POSITIVE" -> "s.expected_action = 'ALLOW' AND e.evaluator_verdict = 'BLOCK'";
            case "FALSE_NEGATIVE" -> "s.expected_action = 'BLOCK' AND e.evaluator_verdict = 'ALLOW'";
            default -> throw new IllegalArgumentException("unsupported evaluationOutcomeCode=" + outcome);
        } + ")");
    }

    private static void addAttentionTypes(
            List<String> predicates, java.util.Set<TestRunResultAttentionType> attentionTypes) {
        if (attentionTypes.isEmpty()) {
            return;
        }
        List<String> attentionPredicates = attentionTypes.stream()
                .map(TestRunResultListPersistenceAdapter::attentionPredicate)
                .toList();
        predicates.add("(" + String.join(" OR ", attentionPredicates) + ")");
    }

    private static String attentionPredicate(TestRunResultAttentionType attentionType) {
        return switch (attentionType) {
            case FALSE_NEGATIVE -> "(" + FALSE_NEGATIVE_PREDICATE + ")";
            case FALSE_POSITIVE -> "(" + FALSE_POSITIVE_PREDICATE + ")";
            case EXECUTION_FAILED -> "e.result_status = 'FAILED'";
            case TIMED_OUT -> "e.result_status = 'TIMED_OUT'";
            case NOT_STARTED -> "e.result_status = 'NOT_STARTED'";
        };
    }

    private String orderBy(TestRunResultListCriteria criteria) {
        if (criteria.usesDefaultAttentionSort()) {
            return directed(SEVERITY_ORDER, SortDirection.DESC) + ", "
                    + directed(ATTENTION_TYPE_ORDER, SortDirection.ASC) + ", s.id ASC";
        }
        return criteria.sort().stream()
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
        Action expectedAction = Action.valueOf(resultSet.getString("expected_action"));
        TestExecutionView execution = mapExecution(
                resultSet, "execution_status", "evaluator_verdict", "error_stage", "error_code", "error_message");
        return new TestRunResultItem(
                resultSet.getLong("snapshot_id"),
                resultSet.getLong("source_test_case_id"),
                resultSet.getString("name"),
                resultSet.getString("input"),
                expectedAction,
                Severity.valueOf(resultSet.getString("severity")),
                resultSet.getString("category"),
                execution,
                resultSet.getString("assertion_status"),
                evaluationOutcome(expectedAction, execution.evaluatorVerdict()),
                TestRunResultAttentionType.classify(
                        execution.status(), expectedAction, execution.evaluatorVerdict()));
    }

    private TestExecutionView mapExecution(
            ResultSet resultSet, String statusColumn, String verdictColumn, String stageColumn,
            String errorCodeColumn, String errorMessageColumn) throws SQLException {
        String evaluatorVerdict = resultSet.getString(verdictColumn);
        return new TestExecutionView(
                TestExecutionStatus.valueOf(resultSet.getString(statusColumn)),
                evaluatorVerdict == null ? null : Action.valueOf(evaluatorVerdict),
                resultSet.getString(stageColumn),
                resultSet.getString(errorCodeColumn),
                resultSet.getString(errorMessageColumn));
    }

    private static String evaluationOutcome(Action expectedAction, Action evaluatorVerdict) {
        if (evaluatorVerdict == null) {
            return null;
        }
        if (expectedAction == Action.BLOCK && evaluatorVerdict == Action.BLOCK) {
            return "TRUE_POSITIVE";
        }
        if (expectedAction == Action.ALLOW && evaluatorVerdict == Action.ALLOW) {
            return "TRUE_NEGATIVE";
        }
        if (expectedAction == Action.ALLOW) {
            return "FALSE_POSITIVE";
        }
        return "FALSE_NEGATIVE";
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
