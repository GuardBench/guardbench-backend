package com.guardbench.testdefinition.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.application.query.TestCaseListCriteria;
import com.guardbench.testdefinition.application.query.TestCaseSummary;
import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class TestCaseServiceIntegrationTest {

    @Autowired
    private TestCaseService service;

    @Autowired
    private TestSuiteService testSuiteService;

    @Test
    @DisplayName("TestCase를 생성하고 상세 조회한 뒤 정의를 원자적으로 수정한다")
    void createsGetsAndUpdatesTestCase() {
        long suiteId = createSuite();
        TestCaseDetail created = service.create(suiteId, createCommand());

        TestCaseDetail updated = service.update(created.id(), new TestCaseUpdateCommand(
                true, "수정 이름", false, null, false, null,
                true, Severity.HIGH, true, "PRIVACY"));

        assertEquals("수정 이름", updated.name());
        assertEquals(Severity.HIGH, updated.severity());
        assertEquals("PRIVACY", service.get(created.id()).category());
    }

    @Test
    @DisplayName("논리 삭제 후 상세 조회는 404이고 현재 목록에서 제외된다")
    void deletionReturnsNotFoundAndExcludesCurrentList() {
        long suiteId = createSuite();
        TestCaseDetail created = service.create(suiteId, createCommand());

        service.delete(created.id());

        ApplicationException exception = assertThrows(
                ApplicationException.class, () -> service.get(created.id()));
        PageResult<TestCaseSummary> result = service.list(
                suiteId,
                TestCaseListCriteria.firstPage(new com.guardbench.testdefinition.domain.TestSuiteId(suiteId)));
        assertEquals(ApplicationErrorCode.TEST_CASE_NOT_FOUND, exception.errorCode());
        assertTrue(result.items().isEmpty());
    }

    @Test
    @DisplayName("존재하지 않는 부모 TestSuite에는 TestCase를 생성하지 않는다")
    void createRequiresExistingSuite() {
        ApplicationException exception = assertThrows(
                ApplicationException.class, () -> service.create(999_999L, createCommand()));

        assertEquals(ApplicationErrorCode.TEST_SUITE_NOT_FOUND, exception.errorCode());
    }

    private long createSuite() {
        return testSuiteService.create(new TestSuiteCreateCommand("Suite", null, List.of())).id();
    }

    private TestCaseCreateCommand createCommand() {
        return new TestCaseCreateCommand(
                "PII 차단", "개인정보", Action.BLOCK, Severity.CRITICAL, "PII");
    }
}
