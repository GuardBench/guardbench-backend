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
    @DisplayName("빈 PostgreSQL에 Flyway V1~V7 스키마를 적용한다")
    void appliesApprovedSchemaToPostgreSql(@Autowired Flyway flyway, @Autowired JdbcTemplate jdbcTemplate) {
        MigrationInfo current = flyway.info().current();
        assertNotNull(current);
        assertEquals("7", current.getVersion().getVersion());
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name IN (
                    'test_suite', 'test_case', 'test_run', 'test_case_snapshot', 'test_execution',
                    'assertion_result', 'change_result', 'quality_gate_result', 'test_run_idempotency',
                    'outbox_event', 'test_run_resolution_claim', 'test_execution_claim',
                    'target_reference', 'bedrock_guardrail_target', 'http_endpoint_target',
                    'evaluator_reference', 'bedrock_guardrail_evaluator')
                """, Integer.class);
        assertEquals(17, tableCount);
    }

    @Test
    @DisplayName("HTTP Endpoint는 host 없는 URL을 DB 단독 저장으로 허용하지 않는다")
    void rejectsHttpEndpointWithoutHost(@Autowired JdbcTemplate jdbcTemplate) {
        String referenceId = "invalid-http-endpoint-url";
        jdbcTemplate.update("INSERT INTO target_reference(reference_id, target_type) VALUES (?, 'HTTP_ENDPOINT')", referenceId);

        assertThrows(DataIntegrityViolationException.class, () -> jdbcTemplate.update(
                "INSERT INTO http_endpoint_target(reference_id, endpoint_url) VALUES (?, 'http://')", referenceId));
    }
}
