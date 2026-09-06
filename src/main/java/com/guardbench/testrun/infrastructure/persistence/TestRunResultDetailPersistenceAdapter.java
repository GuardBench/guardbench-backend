package com.guardbench.testrun.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.guardbench.testrun.application.port.out.LoadTestRunResultDetailPort;
import com.guardbench.testrun.application.port.out.TestExecutionView;
import com.guardbench.testrun.application.port.out.TestRunResultAttentionType;
import com.guardbench.testrun.application.port.out.TestRunResultDetail;
import com.guardbench.testrun.application.port.out.TestRunResultItem;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.TestExecutionStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * TestRun과 Snapshot의 소속을 함께 확인하며 저장된 개별 결과 상세를 조회한다.
 */
@Repository
@Transactional(readOnly = true)
class TestRunResultDetailPersistenceAdapter implements LoadTestRunResultDetailPort {

    private final JdbcTemplate jdbcTemplate;

    TestRunResultDetailPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TestRunResultDetail> load(long testRunId, long snapshotId) {
        String sql = """
                SELECT s.id AS snapshot_id, s.source_test_case_id, s.name, s.input,
                       s.expected_action, s.severity, s.category,
                       e.result_status AS execution_status,
                       e.evaluator_verdict, e.error_stage, e.error_code, e.error_message,
                       e.application_response, ar.assertion_status
                FROM test_case_snapshot s
                JOIN test_execution e ON e.snapshot_id = s.id
                LEFT JOIN assertion_result ar ON ar.snapshot_id = s.id
                WHERE s.test_run_id = ? AND s.id = ?
                """;
        List<TestRunResultDetail> details = jdbcTemplate.query(
                sql, this::mapDetail, testRunId, snapshotId);
        return details.stream().findFirst();
    }

    private TestRunResultDetail mapDetail(ResultSet resultSet, int rowNumber) throws SQLException {
        Action expectedAction = Action.valueOf(resultSet.getString("expected_action"));
        TestExecutionView execution = mapExecution(resultSet);
        TestRunResultItem item = new TestRunResultItem(
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
        return new TestRunResultDetail(item, resultSet.getString("application_response"));
    }

    private TestExecutionView mapExecution(ResultSet resultSet) throws SQLException {
        String evaluatorVerdict = resultSet.getString("evaluator_verdict");
        return new TestExecutionView(
                TestExecutionStatus.valueOf(resultSet.getString("execution_status")),
                evaluatorVerdict == null ? null : Action.valueOf(evaluatorVerdict),
                resultSet.getString("error_stage"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"));
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
}
