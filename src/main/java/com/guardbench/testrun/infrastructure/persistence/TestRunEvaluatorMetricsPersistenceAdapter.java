package com.guardbench.testrun.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.guardbench.testrun.application.port.out.EvaluatorMetricsView;
import com.guardbench.testrun.application.port.out.LoadTestRunEvaluatorMetricsPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * TestRun 전체 Snapshot의 저장된 ExpectedResult와 Evaluator verdict를 PostgreSQL에서 집계한다.
 *
 * <p>verdict가 없는 실행은 어느 분류에도 포함하지 않으며, 결과 목록의 pagination과 무관하게
 * TestRun 전체를 대상으로 한다.
 */
@Repository
@Transactional(readOnly = true)
class TestRunEvaluatorMetricsPersistenceAdapter implements LoadTestRunEvaluatorMetricsPort {

    private static final String SELECT_SQL = """
            SELECT counts.true_positive,
                   counts.true_negative,
                   counts.false_positive,
                   counts.false_negative,
                   CASE
                       WHEN counts.false_positive + counts.true_negative = 0 THEN NULL
                       ELSE counts.false_positive::double precision
                           / (counts.false_positive + counts.true_negative)
                   END AS false_positive_rate,
                   CASE
                       WHEN counts.false_negative + counts.true_positive = 0 THEN NULL
                       ELSE counts.false_negative::double precision
                           / (counts.false_negative + counts.true_positive)
                   END AS false_negative_rate
            FROM (
                SELECT COUNT(*) FILTER (
                           WHERE s.expected_action = 'BLOCK' AND e.evaluator_verdict = 'BLOCK'
                       ) AS true_positive,
                       COUNT(*) FILTER (
                           WHERE s.expected_action = 'ALLOW' AND e.evaluator_verdict = 'ALLOW'
                       ) AS true_negative,
                       COUNT(*) FILTER (
                           WHERE s.expected_action = 'ALLOW' AND e.evaluator_verdict = 'BLOCK'
                       ) AS false_positive,
                       COUNT(*) FILTER (
                           WHERE s.expected_action = 'BLOCK' AND e.evaluator_verdict = 'ALLOW'
                       ) AS false_negative
                FROM test_case_snapshot s
                JOIN test_execution e ON e.snapshot_id = s.id
                WHERE s.test_run_id = ?
            ) counts
            """;

    private final JdbcTemplate jdbcTemplate;

    TestRunEvaluatorMetricsPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public EvaluatorMetricsView load(long testRunId) {
        return jdbcTemplate.queryForObject(SELECT_SQL, this::mapMetrics, testRunId);
    }

    private EvaluatorMetricsView mapMetrics(ResultSet resultSet, int rowNumber) throws SQLException {
        return new EvaluatorMetricsView(
                resultSet.getLong("true_positive"),
                resultSet.getLong("true_negative"),
                resultSet.getLong("false_positive"),
                resultSet.getLong("false_negative"),
                nullableDouble(resultSet, "false_positive_rate"),
                nullableDouble(resultSet, "false_negative_rate"));
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }
}
