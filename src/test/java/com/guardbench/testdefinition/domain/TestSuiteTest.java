package com.guardbench.testdefinition.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TestSuiteTest {

    private static final TestSuiteId TEST_SUITE_ID = new TestSuiteId(1L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-25T11:00:00Z");

    private static TestSuite newTestSuite(String description) {
        return TestSuite.create(TEST_SUITE_ID, "안전성 회귀", description, CREATED_AT);
    }

    @Nested
    @DisplayName("새 TestSuite 생성")
    class Creation {

        @Test
        @DisplayName("이름과 설명을 보유하고 생성 시각을 수정 시각으로 함께 사용한다")
        void keepsNameDescriptionAndUsesCreationInstantAsUpdatedAt() {
            TestSuite testSuite = newTestSuite("PII 차단 정책");

            assertEquals("안전성 회귀", testSuite.name());
            assertEquals("PII 차단 정책", testSuite.description());
            assertEquals(CREATED_AT, testSuite.createdAt());
            assertEquals(CREATED_AT, testSuite.updatedAt());
        }

        @Test
        @DisplayName("생성 시점에 전달한 식별자를 보유한다")
        void keepsIdentifierGivenAtCreation() {
            TestSuite testSuite = newTestSuite(null);

            assertEquals(TEST_SUITE_ID, testSuite.id());
        }

        @Test
        @DisplayName("식별자가 null이면 IllegalArgumentException을 던진다")
        void rejectsNullIdentifier() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TestSuite.create(null, "안전성 회귀", null, CREATED_AT));
        }

        @Test
        @DisplayName("공백만 있는 설명은 값이 없는 것으로 정규화한다")
        void normalizesBlankDescriptionToNull() {
            TestSuite testSuite = newTestSuite("   ");

            assertNull(testSuite.description());
        }

        @Test
        @DisplayName("이름이 공백만 있으면 IllegalArgumentException을 던진다")
        void rejectsBlankName() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TestSuite.create(TEST_SUITE_ID, "   ", null, CREATED_AT));
        }

        @Test
        @DisplayName("이름이 null이면 IllegalArgumentException을 던진다")
        void rejectsNullName() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TestSuite.create(TEST_SUITE_ID, null, null, CREATED_AT));
        }

        @Test
        @DisplayName("생성 시각이 null이면 IllegalArgumentException을 던진다")
        void rejectsNullCreationInstant() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TestSuite.create(TEST_SUITE_ID, "안전성 회귀", null, null));
        }
    }

    @Nested
    @DisplayName("이름 수정")
    class Rename {

        @Test
        @DisplayName("새 이름과 수정 시각을 반영하고 생성 시각은 유지한다")
        void appliesNewNameAndUpdatedAtWhileKeepingCreatedAt() {
            TestSuite testSuite = newTestSuite(null);

            testSuite.rename("안전성 회귀 v2", UPDATED_AT);

            assertEquals("안전성 회귀 v2", testSuite.name());
            assertEquals(UPDATED_AT, testSuite.updatedAt());
            assertEquals(CREATED_AT, testSuite.createdAt());
        }

        @Test
        @DisplayName("공백만 있는 이름으로는 수정할 수 없다")
        void rejectsBlankName() {
            TestSuite testSuite = newTestSuite(null);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> testSuite.rename("  ", UPDATED_AT));
        }

        @Test
        @DisplayName("수정 시각이 생성 시각보다 앞서면 IllegalArgumentException을 던진다")
        void rejectsUpdateInstantBeforeCreatedAt() {
            TestSuite testSuite = newTestSuite(null);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> testSuite.rename("안전성 회귀 v2", CREATED_AT.minusSeconds(1)));
        }

        @Test
        @DisplayName("검증에 실패하면 이름과 수정 시각을 모두 바꾸지 않는다")
        void keepsStateUnchangedWhenValidationFails() {
            TestSuite testSuite = newTestSuite(null);

            assertThrows(
                    IllegalArgumentException.class,
                    () -> testSuite.rename("안전성 회귀 v2", CREATED_AT.minusSeconds(1)));

            assertEquals("안전성 회귀", testSuite.name());
            assertEquals(CREATED_AT, testSuite.updatedAt());
        }
    }

    @Nested
    @DisplayName("설명 수정")
    class ChangeDescription {

        @Test
        @DisplayName("새 설명과 수정 시각을 반영한다")
        void appliesNewDescriptionAndUpdatedAt() {
            TestSuite testSuite = newTestSuite("이전 설명");

            testSuite.changeDescription("새 설명", UPDATED_AT);

            assertEquals("새 설명", testSuite.description());
            assertEquals(UPDATED_AT, testSuite.updatedAt());
        }

        @Test
        @DisplayName("null을 전달하면 설명을 제거한다")
        void removesDescriptionWhenNullGiven() {
            TestSuite testSuite = newTestSuite("이전 설명");

            testSuite.changeDescription(null, UPDATED_AT);

            assertNull(testSuite.description());
        }
    }

    @Nested
    @DisplayName("저장된 상태 복원")
    class Restore {

        @Test
        @DisplayName("식별자와 시각을 그대로 복원한다")
        void restoresIdentifierAndInstants() {
            TestSuite testSuite = TestSuite.restore(
                    new TestSuiteId(11L), "안전성 회귀", "설명", CREATED_AT, UPDATED_AT);

            assertEquals(new TestSuiteId(11L), testSuite.id());
            assertEquals(CREATED_AT, testSuite.createdAt());
            assertEquals(UPDATED_AT, testSuite.updatedAt());
        }

        @Test
        @DisplayName("식별자가 없으면 IllegalArgumentException을 던진다")
        void rejectsMissingIdentifier() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TestSuite.restore(null, "안전성 회귀", null, CREATED_AT, UPDATED_AT));
        }

        @Test
        @DisplayName("수정 시각이 생성 시각보다 앞서면 IllegalArgumentException을 던진다")
        void rejectsUpdatedAtBeforeCreatedAt() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> TestSuite.restore(
                            new TestSuiteId(11L),
                            "안전성 회귀",
                            null,
                            UPDATED_AT,
                            CREATED_AT));
        }
    }
}
