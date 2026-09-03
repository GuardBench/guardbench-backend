package com.guardbench.testdefinition.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.ExpectedResult;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testdefinition.domain.TestCase;
import com.guardbench.testdefinition.domain.TestCaseId;
import com.guardbench.testdefinition.domain.TestSuiteId;

class TestCaseEntityMapperTest {

    private static final TestCaseId TEST_CASE_ID = new TestCaseId(5L);
    private static final TestSuiteId TEST_SUITE_ID = new TestSuiteId(7L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-25T11:00:00Z");

    @Test
    @DisplayName("Domain TestCase를 삭제 상태 없이 Entity로 변환한다")
    void copiesDomainValuesIntoEntity() {
        TestCaseEntity entity = TestCaseEntityMapper.toEntity(testCase());

        assertEquals(5L, entity.id());
        assertEquals(7L, entity.testSuiteId());
        assertEquals("PII 유출 차단", entity.name());
        assertEquals("BLOCK", entity.expectedAction());
        assertEquals("CRITICAL", entity.severity());
        assertEquals(CREATED_AT, entity.createdAt());
        assertEquals(UPDATED_AT, entity.updatedAt());
    }

    @Test
    @DisplayName("Entity를 Domain TestCase로 복원한다")
    void restoresDomainValuesFromEntity() {
        TestCaseEntity entity = new TestCaseEntity(
                5L, 7L, "PII 유출 차단", "입력", "ALLOW", "LOW", "PII",
                CREATED_AT, UPDATED_AT);

        TestCase restored = TestCaseEntityMapper.toDomain(entity);

        assertEquals(TEST_CASE_ID, restored.id());
        assertEquals(TEST_SUITE_ID, restored.testSuiteId());
        assertEquals(Action.ALLOW, restored.expectedResult().action());
        assertEquals(Severity.LOW, restored.severity());
        assertEquals(UPDATED_AT, restored.updatedAt());
    }

    private static TestCase testCase() {
        return TestCase.restore(
                TEST_CASE_ID, TEST_SUITE_ID, "PII 유출 차단", "입력",
                new ExpectedResult(Action.BLOCK), Severity.CRITICAL, "PII", CREATED_AT, UPDATED_AT);
    }
}
