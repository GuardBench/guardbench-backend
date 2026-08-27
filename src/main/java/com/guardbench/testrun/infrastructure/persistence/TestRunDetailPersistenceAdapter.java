package com.guardbench.testrun.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.guardbench.testrun.application.port.out.LoadTestRunDetailPort;
import com.guardbench.testrun.application.port.out.QualityGateMetricsView;
import com.guardbench.testrun.application.port.out.QualityGateView;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testrun.application.port.out.TestRunProgress;
import com.guardbench.testrun.application.port.out.TestRunTargets;
import com.guardbench.testrun.domain.CandidateSource;
import com.guardbench.testrun.domain.TestRunExecutionOutcome;
import com.guardbench.testrun.domain.TestRunStatus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * TestRun 상태·진행률·요약 조회 Port를 PostgreSQL query로 구현한다. Polling 응답에 사용하며 TestCase별
 * 개별 결과 배열은 조회하지 않는다.
 *
 * <p>Quality Gate는 {@code quality_gate_result}를 LEFT JOIN해서 읽고 evaluation Context의 Domain
 * 타입이나 Repository를 사용하지 않는다. {@code NOT_EVALUATED}는 metrics 없이, {@code PASS}/{@code FAIL}은
 * 전체 metrics와 함께 이 Context가 소유한 {@link QualityGateView}로 조합한다.
 *
 * @see <a href="../../../../../../../docs/api/openapi.yaml">GuardBench API V1</a>
 * @see <a href="../../../../../../../docs/decisions/0002-postgresql-persistence-contract.md">ADR 0002</a>
 */
@Repository
@Transactional(readOnly = true)
class TestRunDetailPersistenceAdapter implements LoadTestRunDetailPort {

    private static final String SELECT_SQL = """
            SELECT r.id, r.test_suite_id, r.status, r.test_case_count,
                   r.processed_test_case_count, r.baseline_guardrail_id, r.baseline_version,
                   r.candidate_guardrail_id, r.candidate_requested_source,
                   r.candidate_resolved_version, r.execution_outcome,
                   r.created_at, r.started_at, r.completed_at, r.updated_at,
                   qgr.gate_status, qgr.candidate_assertion_pass_rate,
                   qgr.security_regression_count, qgr.security_regression_rate,
                   qgr.usability_regression_rate, qgr.test_execution_success_rate
            FROM test_run r
            LEFT JOIN quality_gate_result qgr ON qgr.test_run_id = r.id
            WHERE r.id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    TestRunDetailPersistenceAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TestRunDetail> load(long testRunId) {
        List<TestRunDetail> results = jdbcTemplate.query(SELECT_SQL, this::mapDetail, testRunId);
        return results.stream().findFirst();
    }

    private TestRunDetail mapDetail(ResultSet resultSet, int rowNumber) throws SQLException {
        String executionOutcome = resultSet.getString("execution_outcome");
        return new TestRunDetail(
                resultSet.getLong("id"),
                resultSet.getLong("test_suite_id"),
                TestRunStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("test_case_count"),
                new TestRunProgress(
                        resultSet.getInt("processed_test_case_count"),
                        percent(resultSet.getInt("processed_test_case_count"), resultSet.getInt("test_case_count"))),
                mapTargets(resultSet),
                executionOutcome == null ? null : TestRunExecutionOutcome.valueOf(executionOutcome),
                mapQualityGate(resultSet),
                toInstant(resultSet, "created_at"),
                toInstant(resultSet, "started_at"),
                toInstant(resultSet, "completed_at"),
                toInstant(resultSet, "updated_at"));
    }

    private TestRunTargets mapTargets(ResultSet resultSet) throws SQLException {
        return new TestRunTargets(
                new TestRunTargets.BaselineTargetView(
                        resultSet.getString("baseline_guardrail_id"),
                        resultSet.getString("baseline_version")),
                new TestRunTargets.CandidateTargetView(
                        resultSet.getString("candidate_guardrail_id"),
                        CandidateSource.valueOf(resultSet.getString("candidate_requested_source")),
                        resultSet.getString("candidate_resolved_version")));
    }

    private QualityGateView mapQualityGate(ResultSet resultSet) throws SQLException {
        String gateStatus = resultSet.getString("gate_status");
        if (gateStatus == null) {
            return null;
        }
        if ("NOT_EVALUATED".equals(gateStatus)) {
            return new QualityGateView(gateStatus, null);
        }
        QualityGateMetricsView metrics = new QualityGateMetricsView(
                resultSet.getDouble("candidate_assertion_pass_rate"),
                resultSet.getLong("security_regression_count"),
                resultSet.getDouble("security_regression_rate"),
                resultSet.getDouble("usability_regression_rate"),
                resultSet.getDouble("test_execution_success_rate"));
        return new QualityGateView(gateStatus, metrics);
    }

    private static double percent(int processed, int total) {
        return total == 0 ? 0.0 : (processed * 100.0) / total;
    }

    private static Instant toInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
