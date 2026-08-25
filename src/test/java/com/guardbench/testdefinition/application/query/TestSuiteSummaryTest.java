package com.guardbench.testdefinition.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestSuiteSummaryTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-25T11:00:00Z");

    @Test
    @DisplayName("목록 항목이 집계한 TestCase 개수를 함께 보유한다")
    void keepsAggregatedTestCaseCount() {
        TestSuiteSummary summary = new TestSuiteSummary(
                1L, "안전성 회귀", "PII 차단 정책", 253L, CREATED_AT, UPDATED_AT);

        assertEquals(253L, summary.testCaseCount());
        assertEquals("PII 차단 정책", summary.description());
    }

    @Test
    @DisplayName("설명이 없는 TestSuite는 null 설명을 보유한다")
    void keepsAbsentDescriptionAsNull() {
        TestSuiteSummary summary = new TestSuiteSummary(
                1L, "안전성 회귀", null, 0L, CREATED_AT, UPDATED_AT);

        assertNull(summary.description());
    }

    @Test
    @DisplayName("TestCase가 없는 TestSuite의 개수는 0이다")
    void allowsZeroTestCaseCount() {
        TestSuiteSummary summary = new TestSuiteSummary(
                1L, "안전성 회귀", null, 0L, CREATED_AT, UPDATED_AT);

        assertEquals(0L, summary.testCaseCount());
    }

    @Test
    @DisplayName("TestCase 개수가 음수면 IllegalArgumentException을 던진다")
    void rejectsNegativeTestCaseCount() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestSuiteSummary(
                        1L, "안전성 회귀", null, -1L, CREATED_AT, UPDATED_AT));
    }

    @Test
    @DisplayName("식별자가 양수가 아니면 IllegalArgumentException을 던진다")
    void rejectsNonPositiveIdentifier() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestSuiteSummary(
                        0L, "안전성 회귀", null, 0L, CREATED_AT, UPDATED_AT));
    }
}
