package com.guardbench.testdefinition.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testdefinition.application.query.TestSuiteSummary;
import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class TestSuiteServiceIntegrationTest {

    @Autowired
    private TestSuiteService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("TestSuite와 초기 TestCase를 한 트랜잭션에서 생성하고 활성 개수를 조회한다")
    void createsSuiteAndInitialTestCasesAtomically() {
        TestSuiteSummary created = service.create(new TestSuiteCreateCommand(
                "Safety Suite",
                "안전성 테스트",
                List.of(
                        testCase("PII 차단", "개인정보", Action.BLOCK, Severity.CRITICAL, "PII"),
                        testCase("일반 허용", "안녕하세요", Action.ALLOW, Severity.LOW, "GENERAL"))));

        TestSuiteSummary detail = service.get(created.id());

        assertEquals(2L, created.testCaseCount());
        assertEquals(2L, detail.testCaseCount());
        assertEquals(2L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_case WHERE test_suite_id = ?",
                Long.class,
                created.id()));
    }

    @Test
    @DisplayName("초기 TestCase 하나가 유효하지 않으면 TestSuite도 저장하지 않는다")
    void rejectsWholeCreationBeforeSavingInvalidInitialTestCase() {
        long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM test_suite", Long.class);
        TestSuiteCreateCommand command = new TestSuiteCreateCommand(
                "Atomic Suite",
                null,
                List.of(
                        testCase("정상", "입력", Action.ALLOW, Severity.LOW, "GENERAL"),
                        testCase("오류", "입력", Action.BLOCK, Severity.HIGH, "\u00a0")));

        assertThrows(IllegalArgumentException.class, () -> service.create(command));

        assertEquals(before, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM test_suite", Long.class));
    }

    @Test
    @DisplayName("description의 명시적 null 수정은 설명을 제거하고 TestCase 개수를 유지한다")
    void removesDescriptionAndKeepsTestCaseCount() {
        TestSuiteSummary created = service.create(new TestSuiteCreateCommand(
                "Suite", "설명", List.of(testCase(
                        "Case", "입력", Action.ALLOW, Severity.MEDIUM, "GENERAL"))));

        TestSuiteSummary updated = service.update(
                created.id(), new TestSuiteUpdateCommand(false, null, true, null));

        assertNull(updated.description());
        assertEquals(1L, updated.testCaseCount());
    }

    private static TestCaseCreateCommand testCase(
            String name,
            String input,
            Action action,
            Severity severity,
            String category) {
        return new TestCaseCreateCommand(name, input, action, severity, category);
    }
}
