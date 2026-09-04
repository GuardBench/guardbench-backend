package com.guardbench;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * V12까지 적용된 상태에서 legacy {@code evaluator_reference}/{@code test_run} 데이터가 있을 때 V13
 * migration({@code evaluator_type} → {@code provider_code}/{@code model_id} 교체)이 성공하는지
 * 검증한다.
 *
 * <p>Spring Boot 자동 Flyway는 컨텍스트 로딩 시점에 전체 migration을 한 번에 적용하므로, 이 시나리오는
 * Spring context 없이 별도 {@link PostgreSQLContainer}에 Flyway API를 두 단계(V12 target → 전체)로
 * 직접 실행해 검증한다.
 */
class V13MigrationUpgradeIntegrationTest {

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("guardbench")
            .withUsername("guardbench")
            .withPassword("guardbench");

    private static HikariDataSource dataSource;

    @BeforeAll
    static void startContainer() {
        POSTGRES.start();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername(POSTGRES.getUsername());
        config.setPassword(POSTGRES.getPassword());
        dataSource = new HikariDataSource(config);
    }

    @AfterAll
    static void stopContainer() {
        if (dataSource != null) {
            dataSource.close();
        }
        POSTGRES.stop();
    }

    @Test
    @DisplayName("V12까지 적용된 상태에서 legacy evaluator_reference/test_run 행이 있어도 V13이 성공한다")
    void migratesSuccessfullyWithLegacyDataPresent() {
        Flyway toV12 = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("12"))
                .load();
        toV12.migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        Instant now = Instant.now();
        Timestamp createdAt = Timestamp.from(now);

        jdbcTemplate.update(
                "INSERT INTO test_suite(id, name, created_at, updated_at) VALUES (?, ?, ?, ?)",
                9001L, "legacy-suite", createdAt, createdAt);
        jdbcTemplate.update(
                "INSERT INTO test_case(id, test_suite_id, name, input, expected_action, severity, category, "
                        + "created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                9001L, 9001L, "case", "input", "ALLOW", "HIGH", "category", createdAt, createdAt);
        jdbcTemplate.update(
                "INSERT INTO target_reference(reference_id, target_type) VALUES (?, 'HTTP_ENDPOINT')",
                "legacy-target-ref");
        jdbcTemplate.update(
                "INSERT INTO http_endpoint_target(reference_id, endpoint_url, requested_revision, model) "
                        + "VALUES (?, 'https://example.com/v1/chat/completions', 'v1', 'legacy-model')",
                "legacy-target-ref");
        jdbcTemplate.update(
                "INSERT INTO evaluator_reference(reference_id, evaluator_type) VALUES (?, 'BEDROCK_GUARDRAIL')",
                "legacy-evaluator-ref");
        jdbcTemplate.update(
                "INSERT INTO bedrock_guardrail_evaluator(reference_id, guardrail_identifier, guardrail_revision) "
                        + "VALUES (?, 'guardrail-legacy', '1')",
                "legacy-evaluator-ref");
        jdbcTemplate.update(
                "INSERT INTO test_run(id, test_suite_id, status, test_case_count, processed_test_case_count, "
                        + "target_reference_id, evaluation_checks, evaluation_strictness, evaluator_reference_id, "
                        + "created_at, updated_at) VALUES (?, ?, 'QUEUED', 1, 0, ?, 'PII_LEAKAGE', 'STANDARD', ?, ?, ?)",
                9001L, 9001L, "legacy-target-ref", "legacy-evaluator-ref", createdAt, createdAt);

        Flyway toLatest = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load();

        assertDoesNotThrow(toLatest::migrate);

        Integer evaluatorReferenceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evaluator_reference", Integer.class);
        Integer testRunCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_run", Integer.class);
        assertEquals(0, evaluatorReferenceCount);
        assertEquals(0, testRunCount);

        Integer legacyColumnCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'evaluator_reference' AND column_name = 'evaluator_type'",
                Integer.class);
        assertEquals(0, legacyColumnCount);
    }
}
