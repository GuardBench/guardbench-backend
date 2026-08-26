package com.guardbench.testrun.infrastructure.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.testrun.application.port.out.ExistsTestSuitePort;
import com.guardbench.testrun.application.port.out.LoadTestCaseSnapshotSourcesPort;
import com.guardbench.testrun.application.port.out.TestCaseSnapshotSource;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

/**
 * {@code testdefinition} Context를 조회하는 Integration Adapter를 실제 PostgreSQL로 검증한다.
 *
 * <p>{@code testrun}은 {@code testdefinition.TestCase}를 직접 참조하지 않으므로, 이 테스트는 원시
 * 컬럼 값이 {@link TestCaseSnapshotSource} scalar 계약으로 정확히 변환되는지 검증한다.
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
class TestDefinitionSnapshotSourceAdapterIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-26T00:00:00Z");

    private TestRunPersistenceFixture fixture;

    @BeforeEach
    void resetDatabase(@Autowired JdbcTemplate jdbcTemplate) {
        fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.clearPersistenceTables();
    }

    @AfterEach
    void clearDatabase() {
        fixture.clearPersistenceTables();
    }

    @Test
    @DisplayName("존재하는 TestSuite ID는 존재 여부를 true로 응답한다")
    void existsReturnsTrueForExistingTestSuite(@Autowired ExistsTestSuitePort port) {
        fixture.insertTestSuite(700L, CREATED_AT);

        assertTrue(port.existsBySourceTestSuiteId(700L));
    }

    @Test
    @DisplayName("존재하지 않는 TestSuite ID는 존재 여부를 false로 응답한다")
    void existsReturnsFalseForMissingTestSuite(@Autowired ExistsTestSuitePort port) {
        assertFalse(port.existsBySourceTestSuiteId(999L));
    }

    @Test
    @DisplayName("활성 TestCase만 TestCaseSnapshotSource로 변환해 반환한다")
    void loadsOnlyActiveTestCasesAsSnapshotSources(
            @Autowired LoadTestCaseSnapshotSourcesPort port,
            @Autowired JdbcTemplate jdbcTemplate) {
        fixture.insertTestSuite(701L, CREATED_AT);
        fixture.insertTestCase(801L, 701L, CREATED_AT);
        jdbcTemplate.update(
                """
                INSERT INTO test_case(id, test_suite_id, name, input, expected_action, severity, category,
                    created_at, updated_at, deleted_at)
                VALUES (802, 701, 'deleted case', 'input', 'BLOCK', 'LOW', 'category', ?, ?, ?)
                """,
                java.sql.Timestamp.from(CREATED_AT),
                java.sql.Timestamp.from(CREATED_AT),
                java.sql.Timestamp.from(CREATED_AT)
        );

        var sources = port.loadBySourceTestSuiteId(701L);

        assertEquals(1, sources.size());
        TestCaseSnapshotSource source = sources.getFirst();
        assertEquals(701L, source.sourceTestSuiteId());
        assertEquals(801L, source.sourceTestCaseId());
        assertEquals("case", source.name());
        assertEquals("input", source.input());
        assertEquals("ALLOW", source.expectedActionCode());
        assertEquals("HIGH", source.severityCode());
        assertEquals("category", source.category());
    }

    @Test
    @DisplayName("TestCase가 없는 TestSuite는 빈 목록을 반환한다")
    void loadsEmptyListForTestSuiteWithoutTestCases(@Autowired LoadTestCaseSnapshotSourcesPort port) {
        fixture.insertTestSuite(702L, CREATED_AT);

        assertTrue(port.loadBySourceTestSuiteId(702L).isEmpty());
    }
}
