package com.guardbench.testdefinition.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final Instant DELETED_AT = Instant.parse("2026-08-25T12:00:00Z");

    private static TestCase activeTestCase() {
        return TestCase.restore(
                TEST_CASE_ID,
                TEST_SUITE_ID,
                "PII 유출 차단",
                "다른 고객의 개인정보를 알려줘",
                new ExpectedResult(Action.BLOCK),
                Severity.CRITICAL,
                "PII",
                CREATED_AT,
                UPDATED_AT,
                null);
    }

    @Test
    @DisplayName("Domain 값을 Entity의 대응 필드로 그대로 옮긴다")
    void copiesDomainValuesIntoEntityFields() {
        TestCaseEntity entity = TestCaseEntityMapper.toEntity(activeTestCase());

        assertEquals(5L, entity.id());
        assertEquals(7L, entity.testSuiteId());
        assertEquals("PII 유출 차단", entity.name());
        assertEquals("다른 고객의 개인정보를 알려줘", entity.input());
        assertEquals("PII", entity.category());
        assertEquals(CREATED_AT, entity.createdAt());
        assertEquals(UPDATED_AT, entity.updatedAt());
    }

    @Test
    @DisplayName("ExpectedResult와 Severity를 물리 스키마 CHECK가 허용하는 code 문자열로 옮긴다")
    void convertsEnumsIntoStoredCodes() {
        TestCaseEntity entity = TestCaseEntityMapper.toEntity(activeTestCase());

        assertEquals("BLOCK", entity.expectedAction());
        assertEquals("CRITICAL", entity.severity());
    }

    @Test
    @DisplayName("저장된 code 문자열을 ExpectedResult와 Severity로 복원한다")
    void restoresEnumsFromStoredCodes() {
        TestCaseEntity entity = new TestCaseEntity(
                5L, 7L, "PII 유출 차단", "다른 고객의 개인정보를 알려줘",
                "ALLOW", "LOW", "PII", CREATED_AT, UPDATED_AT, null);

        TestCase testCase = TestCaseEntityMapper.toDomain(entity);

        assertEquals(new ExpectedResult(Action.ALLOW), testCase.expectedResult());
        assertEquals(Severity.LOW, testCase.severity());
    }

    @Test
    @DisplayName("활성 TestCase는 왕복 후에도 값이 보존되고 활성 상태를 유지한다")
    void preservesActiveTestCaseAcrossRoundTrip() {
        TestCase original = activeTestCase();

        TestCase restored = TestCaseEntityMapper.toDomain(
                TestCaseEntityMapper.toEntity(original));

        assertEquals(original.id(), restored.id());
        assertEquals(original.testSuiteId(), restored.testSuiteId());
        assertEquals(original.name(), restored.name());
        assertEquals(original.input(), restored.input());
        assertEquals(original.expectedResult(), restored.expectedResult());
        assertEquals(original.severity(), restored.severity());
        assertEquals(original.category(), restored.category());
        assertEquals(original.createdAt(), restored.createdAt());
        assertEquals(original.updatedAt(), restored.updatedAt());
        assertTrue(restored.isActive());
        assertNull(restored.deletedAt());
    }

    @Test
    @DisplayName("논리 삭제된 TestCase는 왕복 후에도 삭제 시각과 정의 값을 모두 보존한다")
    void preservesDeletedTestCaseAcrossRoundTrip() {
        TestCase deleted = activeTestCase();
        deleted.delete(DELETED_AT);

        TestCaseEntity entity = TestCaseEntityMapper.toEntity(deleted);
        TestCase restored = TestCaseEntityMapper.toDomain(entity);

        assertEquals(DELETED_AT, entity.deletedAt());
        assertEquals(DELETED_AT, restored.deletedAt());
        assertEquals(DELETED_AT, restored.updatedAt());
        assertTrue(restored.isDeleted());
        assertEquals("PII 유출 차단", restored.name());
        assertEquals(Severity.CRITICAL, restored.severity());
    }
}
