package com.guardbench.testdefinition.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.guardbench.testdefinition.domain.TestSuite;
import com.guardbench.testdefinition.domain.TestSuiteId;

class TestSuiteEntityMapperTest {

    private static final TestSuiteId TEST_SUITE_ID = new TestSuiteId(7L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-25T11:00:00Z");

    @Test
    @DisplayName("Domain 값을 Entity의 대응 필드로 그대로 옮긴다")
    void copiesDomainValuesIntoEntityFields() {
        TestSuite testSuite = TestSuite.restore(
                TEST_SUITE_ID, "안전성 회귀", "PII 차단 정책", CREATED_AT, UPDATED_AT);

        TestSuiteEntity entity = TestSuiteEntityMapper.toEntity(testSuite);

        assertEquals(7L, entity.id());
        assertEquals("안전성 회귀", entity.name());
        assertEquals("PII 차단 정책", entity.description());
        assertEquals(CREATED_AT, entity.createdAt());
        assertEquals(UPDATED_AT, entity.updatedAt());
    }

    @Test
    @DisplayName("Entity 값을 Domain의 대응 값으로 복원한다")
    void restoresDomainValuesFromEntityFields() {
        TestSuiteEntity entity = new TestSuiteEntity(
                7L, "안전성 회귀", "PII 차단 정책", CREATED_AT, UPDATED_AT);

        TestSuite testSuite = TestSuiteEntityMapper.toDomain(entity);

        assertEquals(TEST_SUITE_ID, testSuite.id());
        assertEquals("안전성 회귀", testSuite.name());
        assertEquals("PII 차단 정책", testSuite.description());
        assertEquals(CREATED_AT, testSuite.createdAt());
        assertEquals(UPDATED_AT, testSuite.updatedAt());
    }

    @Test
    @DisplayName("Domain에서 Entity를 거쳐 다시 Domain으로 왕복해도 값이 보존된다")
    void preservesValuesAcrossRoundTrip() {
        TestSuite original = TestSuite.restore(
                TEST_SUITE_ID, "안전성 회귀", "PII 차단 정책", CREATED_AT, UPDATED_AT);

        TestSuite restored = TestSuiteEntityMapper.toDomain(
                TestSuiteEntityMapper.toEntity(original));

        assertEquals(original.id(), restored.id());
        assertEquals(original.name(), restored.name());
        assertEquals(original.description(), restored.description());
        assertEquals(original.createdAt(), restored.createdAt());
        assertEquals(original.updatedAt(), restored.updatedAt());
    }

    @Test
    @DisplayName("설명이 없는 TestSuite는 왕복 후에도 설명이 없다")
    void keepsAbsentDescriptionAcrossRoundTrip() {
        TestSuite original = TestSuite.restore(
                TEST_SUITE_ID, "안전성 회귀", null, CREATED_AT, UPDATED_AT);

        TestSuiteEntity entity = TestSuiteEntityMapper.toEntity(original);
        TestSuite restored = TestSuiteEntityMapper.toDomain(entity);

        assertNull(entity.description());
        assertNull(restored.description());
    }
}
