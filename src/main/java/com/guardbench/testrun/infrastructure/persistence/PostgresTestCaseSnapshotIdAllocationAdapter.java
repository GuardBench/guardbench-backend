package com.guardbench.testrun.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.NextTestCaseSnapshotIdPort;
import com.guardbench.testrun.domain.TestCaseSnapshotId;

@Repository
class PostgresTestCaseSnapshotIdAllocationAdapter implements NextTestCaseSnapshotIdPort {
    private final JdbcTemplate jdbcTemplate;

    PostgresTestCaseSnapshotIdAllocationAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TestCaseSnapshotId nextId() {
        Long value = jdbcTemplate.queryForObject("SELECT nextval('test_case_snapshot_id_seq')", Long.class);
        return new TestCaseSnapshotId(value);
    }
}
