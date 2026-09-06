package com.guardbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class PersistenceFoundationIntegrationTest {

    @Test
    @DisplayName("빈 PostgreSQL에 V1부터 V3까지 Flyway 스키마를 순서대로 적용한다")
    void appliesApprovedSchemaToPostgreSql(@Autowired Flyway flyway, @Autowired JdbcTemplate jdbcTemplate) {
        MigrationInfo current = flyway.info().current();
        assertNotNull(current);
        assertEquals("3", current.getVersion().getVersion());
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN (
                    'test_suite', 'test_case', 'test_run', 'test_case_snapshot', 'test_execution',
                    'assertion_result', 'change_result', 'quality_gate_result', 'test_run_idempotency',
                    'outbox_event', 'test_run_resolution_claim', 'test_execution_claim',
                    'target_reference', 'http_endpoint_target', 'evaluator_reference',
                    'test_case_bulk_idempotency')
                """, Integer.class);
        assertEquals(16, tableCount);
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('bedrock_guardrail_target', 'bedrock_guardrail_evaluator')
                """, Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'test_case'
                  AND column_name = 'deleted_at'
                """, Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND constraint_name IN ('fk_snapshot_source_test_case', 'fk_test_run_suite')
                """, Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'test_run'
                  AND column_name IN ('assertion_pass_rate_threshold', 'execution_success_rate_threshold')
                """, Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'test_run'
                  AND column_name IN ('assertion_pass_rate_threshold', 'execution_success_rate_threshold')
                  AND column_default = '0.95'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND constraint_name = 'ck_test_run_quality_gate_thresholds'
                """, Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_schema = 'public'
                  AND constraint_name = 'ck_test_case_bulk_idempotency_completion'
                """, Integer.class));
    }

    @Test
    @DisplayName("기존 V2 PostgreSQL에는 checksum 오류 없이 V3만 적용한다")
    void upgradesExistingV2SchemaWithoutChecksumMismatch(
            @Autowired Flyway applicationFlyway,
            @Autowired JdbcTemplate jdbcTemplate) {
        String schema = "flyway_upgrade_test";
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        jdbcTemplate.execute("CREATE SCHEMA " + schema);

        try {
            Flyway v2Flyway = Flyway.configure()
                    .dataSource(applicationFlyway.getConfiguration().getDataSource())
                    .defaultSchema(schema)
                    .schemas(schema)
                    .locations("classpath:db/migration")
                    .target("2")
                    .load();
            v2Flyway.migrate();

            MigrationInfo appliedV2 = v2Flyway.info().current();
            assertEquals("2", appliedV2.getVersion().getVersion());
            assertNotNull(appliedV2.getChecksum());
            assertEquals(0, jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema = ? AND table_name = 'test_case_bulk_idempotency'
                    """, Integer.class, schema));

            Flyway upgradeFlyway = Flyway.configure()
                    .dataSource(applicationFlyway.getConfiguration().getDataSource())
                    .defaultSchema(schema)
                    .schemas(schema)
                    .locations("classpath:db/migration")
                    .load();
            upgradeFlyway.migrate();

            assertEquals("3", upgradeFlyway.info().current().getVersion().getVersion());
            assertEquals(1, jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM information_schema.tables
                    WHERE table_schema = ? AND table_name = 'test_case_bulk_idempotency'
                    """, Integer.class, schema));
            assertEquals(1, jdbcTemplate.queryForObject("""
                    SELECT count(*) FROM information_schema.table_constraints
                    WHERE table_schema = ? AND constraint_name = 'ck_test_case_bulk_idempotency_completion'
                    """, Integer.class, schema));
        } finally {
            jdbcTemplate.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
        }
    }

    @Test
    @DisplayName("HTTP Endpoint는 host 없는 URL을 DB 단독 저장으로 허용하지 않는다")
    void rejectsHttpEndpointWithoutHost(@Autowired JdbcTemplate jdbcTemplate) {
        String referenceId = "invalid-http-endpoint-url";
        jdbcTemplate.update("INSERT INTO target_reference(reference_id, target_type) VALUES (?, 'HTTP_ENDPOINT')", referenceId);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO http_endpoint_target(reference_id, endpoint_url, model) VALUES (?, 'http://', 'test-model')", referenceId));
    }

    @Test
    @DisplayName("HTTP Endpoint는 model 없는 저장을 허용하지 않는다")
    void rejectsHttpEndpointWithoutModel(@Autowired JdbcTemplate jdbcTemplate) {
        String referenceId = "missing-http-endpoint-model";
        jdbcTemplate.update("INSERT INTO target_reference(reference_id, target_type) VALUES (?, 'HTTP_ENDPOINT')", referenceId);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO http_endpoint_target(reference_id, endpoint_url) VALUES (?, 'https://example.com/v1/chat/completions')",
                referenceId));
    }
}
