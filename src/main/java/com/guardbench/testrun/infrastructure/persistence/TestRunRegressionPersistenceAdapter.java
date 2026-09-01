package com.guardbench.testrun.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.guardbench.testrun.application.port.out.LoadTestRunRegressionPort;
import com.guardbench.testrun.application.port.out.PageCriteria;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.TargetReferenceView;
import com.guardbench.testrun.application.port.out.TestRunRegressionSnapshot;
import com.guardbench.testrun.application.port.out.TestRunRegressionView;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.EvaluationProfile;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.TestRunStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** TestRun의 Snapshot 정의·고정 Evaluator 설정·저장 verdict를 Regression 조회 모델로 읽는다. */
@Repository
@Transactional(readOnly = true)
class TestRunRegressionPersistenceAdapter implements LoadTestRunRegressionPort {

    private static final String RUN_SELECT = """
            SELECT r.id, r.test_suite_id, r.status, r.target_reference_id, tr.target_type,
                   COALESCE(bg.guardrail_identifier, he.endpoint_url) AS target_identifier,
                   COALESCE(bg.requested_revision, he.requested_revision) AS target_revision,
                   he.model AS target_model, r.evaluation_checks, r.evaluation_strictness,
                   r.completed_at,
                   CASE WHEN er.reference_id IS NULL OR be.reference_id IS NULL THEN NULL
                        ELSE er.evaluator_type || '|' || be.guardrail_identifier || '|' || be.guardrail_revision
                   END AS evaluator_config_key
            FROM test_run r
            JOIN target_reference tr ON tr.reference_id = r.target_reference_id
            LEFT JOIN bedrock_guardrail_target bg ON bg.reference_id = tr.reference_id
            LEFT JOIN http_endpoint_target he ON he.reference_id = tr.reference_id
            LEFT JOIN evaluator_reference er ON er.reference_id = r.evaluator_reference_id
            LEFT JOIN bedrock_guardrail_evaluator be ON be.reference_id = er.reference_id
            WHERE r.id = ?
            """;

    private static final String COMPARABLE_SELECT = """
            SELECT r.id, r.test_suite_id, r.status, r.target_reference_id, tr.target_type,
                   COALESCE(bg.guardrail_identifier, he.endpoint_url) AS target_identifier,
                   COALESCE(bg.requested_revision, he.requested_revision) AS target_revision,
                   he.model AS target_model, r.evaluation_checks, r.evaluation_strictness,
                   r.completed_at,
                   er.evaluator_type || '|' || be.guardrail_identifier || '|' || be.guardrail_revision
                       AS evaluator_config_key
            FROM test_run r
            JOIN target_reference tr ON tr.reference_id = r.target_reference_id
            LEFT JOIN bedrock_guardrail_target bg ON bg.reference_id = tr.reference_id
            LEFT JOIN http_endpoint_target he ON he.reference_id = tr.reference_id
            JOIN evaluator_reference er ON er.reference_id = r.evaluator_reference_id
            JOIN bedrock_guardrail_evaluator be ON be.reference_id = er.reference_id
            WHERE r.id <> ?
              AND r.status = 'FINISHED'
              AND r.test_case_count = (SELECT test_case_count FROM test_run WHERE id = ?)
              AND er.evaluator_type = (
                  SELECT er0.evaluator_type
                  FROM test_run r0
                  JOIN evaluator_reference er0 ON er0.reference_id = r0.evaluator_reference_id
                  WHERE r0.id = ?
              )
              AND be.guardrail_identifier = (
                  SELECT be0.guardrail_identifier
                  FROM test_run r0
                  JOIN bedrock_guardrail_evaluator be0 ON be0.reference_id = r0.evaluator_reference_id
                  WHERE r0.id = ?
              )
              AND be.guardrail_revision = (
                  SELECT be0.guardrail_revision
                  FROM test_run r0
                  JOIN bedrock_guardrail_evaluator be0 ON be0.reference_id = r0.evaluator_reference_id
                  WHERE r0.id = ?
              )
              AND (r.completed_at, r.id) < (
                  SELECT r0.completed_at, r0.id
                  FROM test_run r0
                  WHERE r0.id = ?
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM test_case_snapshot current_snapshot
                  WHERE current_snapshot.test_run_id = ?
                    AND NOT EXISTS (
                        SELECT 1
                        FROM test_case_snapshot candidate_snapshot
                        WHERE candidate_snapshot.test_run_id = r.id
                          AND candidate_snapshot.source_test_case_id = current_snapshot.source_test_case_id
                          AND candidate_snapshot.name = current_snapshot.name
                          AND candidate_snapshot.input = current_snapshot.input
                          AND candidate_snapshot.expected_action = current_snapshot.expected_action
                          AND candidate_snapshot.severity = current_snapshot.severity
                          AND candidate_snapshot.category = current_snapshot.category
                    )
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM test_case_snapshot candidate_snapshot
                  WHERE candidate_snapshot.test_run_id = r.id
                    AND NOT EXISTS (
                        SELECT 1
                        FROM test_case_snapshot current_snapshot
                        WHERE current_snapshot.test_run_id = ?
                          AND current_snapshot.source_test_case_id = candidate_snapshot.source_test_case_id
                          AND current_snapshot.name = candidate_snapshot.name
                          AND current_snapshot.input = candidate_snapshot.input
                          AND current_snapshot.expected_action = candidate_snapshot.expected_action
                          AND current_snapshot.severity = candidate_snapshot.severity
                          AND current_snapshot.category = candidate_snapshot.category
                    )
              )
            ORDER BY r.completed_at DESC, r.id DESC
            LIMIT ? OFFSET ?
            """;

    private static final String SNAPSHOT_SELECT = """
            SELECT s.id, s.source_test_case_id, s.name, s.input, s.expected_action,
                   s.severity, s.category, e.evaluator_verdict
            FROM test_case_snapshot s
            LEFT JOIN test_execution e ON e.snapshot_id = s.id
            WHERE s.test_run_id = ?
            ORDER BY s.source_test_case_id, s.id
            """;

    private final JdbcTemplate jdbcTemplate;

    TestRunRegressionPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TestRunRegressionView> loadRun(long testRunId) {
        return jdbcTemplate.query(RUN_SELECT, this::mapRun, testRunId).stream().findFirst();
    }

    @Override
    public List<TestRunRegressionSnapshot> loadSnapshots(long testRunId) {
        return jdbcTemplate.query(SNAPSHOT_SELECT, this::mapSnapshot, testRunId);
    }

    @Override
    public PageResult<TestRunRegressionView> loadComparableRuns(long testRunId, PageCriteria page) {
        List<TestRunRegressionView> items = jdbcTemplate.query(
                COMPARABLE_SELECT,
                this::mapRun,
                testRunId, testRunId, testRunId, testRunId, testRunId,
                testRunId, testRunId, testRunId, page.size(), page.offset());
        String countSql = "SELECT COUNT(*) FROM (" + COMPARABLE_SELECT
                .replace("LIMIT ? OFFSET ?", "") + ") candidates";
        long totalElements = jdbcTemplate.queryForObject(
                countSql,
                Long.class,
                testRunId, testRunId, testRunId, testRunId, testRunId,
                testRunId, testRunId, testRunId);
        return PageResult.of(items, page, totalElements);
    }

    private TestRunRegressionView mapRun(ResultSet resultSet, int rowNumber) throws SQLException {
        String checks = resultSet.getString("evaluation_checks");
        String strictness = resultSet.getString("evaluation_strictness");
        EvaluationProfile profile = checks == null
                ? null
                : new EvaluationProfile(List.of(checks.split(",")), strictness);
        return new TestRunRegressionView(
                resultSet.getLong("id"),
                resultSet.getLong("test_suite_id"),
                TestRunStatus.valueOf(resultSet.getString("status")),
                new TargetReferenceView(
                        resultSet.getString("target_reference_id"),
                        resultSet.getString("target_type"),
                        resultSet.getString("target_identifier"),
                        resultSet.getString("target_revision"),
                        resultSet.getString("target_model")),
                profile,
                resultSet.getString("evaluator_config_key"),
                toInstant(resultSet, "completed_at"));
    }

    private TestRunRegressionSnapshot mapSnapshot(ResultSet resultSet, int rowNumber) throws SQLException {
        String verdict = resultSet.getString("evaluator_verdict");
        return new TestRunRegressionSnapshot(
                resultSet.getLong("id"),
                resultSet.getLong("source_test_case_id"),
                resultSet.getString("name"),
                resultSet.getString("input"),
                Action.valueOf(resultSet.getString("expected_action")),
                Severity.valueOf(resultSet.getString("severity")),
                resultSet.getString("category"),
                verdict == null ? null : Action.valueOf(verdict));
    }

    private static java.time.Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
