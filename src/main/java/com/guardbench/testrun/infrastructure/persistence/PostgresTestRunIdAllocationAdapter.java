package com.guardbench.testrun.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.guardbench.testrun.application.port.out.NextTestRunIdPort;
import com.guardbench.testrun.domain.TestRunId;

@Repository
class PostgresTestRunIdAllocationAdapter implements NextTestRunIdPort {
    private final JdbcTemplate jdbcTemplate;

    PostgresTestRunIdAllocationAdapter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public TestRunId nextId() {
        Long value = jdbcTemplate.queryForObject("SELECT nextval('test_run_id_seq')", Long.class);
        return new TestRunId(value);
    }
}
