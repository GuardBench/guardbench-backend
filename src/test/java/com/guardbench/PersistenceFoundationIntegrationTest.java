package com.guardbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class PersistenceFoundationIntegrationTest {

    @Test
    @DisplayName("빈 PostgreSQL에 승인된 Flyway V1/V2 스키마를 적용한다")
    void appliesApprovedSchemaToPostgreSql(
            @Autowired Flyway flyway,
            @Autowired JdbcTemplate jdbcTemplate) {
        MigrationInfo current = flyway.info().current();

        assertNotNull(current);
        assertEquals("2", current.getVersion().getVersion());

        Integer tableCount = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN (
                      'test_suite', 'test_case', 'test_run',
                      'test_case_snapshot', 'test_execution',
                      'assertion_result', 'change_result',
                      'quality_gate_result', 'test_run_idempotency',
                      'outbox_event', 'test_run_resolution_claim',
                      'test_execution_claim'
                  )
                """,
                Integer.class);

        assertEquals(12, tableCount);

        Integer suiteCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM test_suite", Integer.class);

        assertEquals(0, suiteCount);
    }
}
