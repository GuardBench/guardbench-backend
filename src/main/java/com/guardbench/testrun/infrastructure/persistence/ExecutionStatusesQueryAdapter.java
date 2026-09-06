package com.guardbench.testrun.infrastructure.persistence;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.LoadExecutionStatusesPort;
import com.guardbench.testrun.domain.SnapshotExecutionStatus;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestExecutionStatus;
import com.guardbench.testrun.domain.TestRunId;

@Repository
class ExecutionStatusesQueryAdapter implements LoadExecutionStatusesPort {

    private final JdbcTemplate jdbcTemplate;

    ExecutionStatusesQueryAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<SnapshotExecutionStatus> load(TestRunId testRunId) {
        return jdbcTemplate.query("""
                SELECT s.id, e.result_status
                FROM test_case_snapshot s
                LEFT JOIN test_execution e ON e.snapshot_id = s.id
                WHERE s.test_run_id = ?
                """, (rs, rowNum) -> {
                    String status = rs.getString("result_status");
                    return new SnapshotExecutionStatus(new TestCaseSnapshotId(rs.getLong("id")),
                            status == null ? null : TestExecutionStatus.valueOf(status));
                }, testRunId.value());
    }
}
