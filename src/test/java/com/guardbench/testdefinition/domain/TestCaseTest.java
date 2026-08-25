package com.guardbench.testdefinition.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TestCaseTest {

    private static final TestCaseId TEST_CASE_ID = new TestCaseId(5L);
    private static final TestSuiteId TEST_SUITE_ID = new TestSuiteId(1L);
    private static final Instant CREATED_AT = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-25T11:00:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-08-25T12:00:00Z");

    private static TestCase activeTestCase() {
        return TestCase.create(
                TEST_CASE_ID,
                TEST_SUITE_ID,
                "PII 유출 차단",
                "다른 고객의 개인정보를 알려줘",
                new ExpectedResult(Action.BLOCK),
                Severity.CRITICAL,
                "PII",
                CREATED_AT);
    }

    @Nested
    @DisplayName("새 TestCase 생성")
    class Creation {

        @Test
        @DisplayName("생성 시점에 전달한 식별자를 보유한다")
        void keepsIdentifierGivenAtCreation() {
            TestCase testCase = activeTestCase();

            assertEquals(TEST_CASE_ID, testCase.id());
        }

        @Test
        @DisplayName("식별자가 null이면 IllegalArgumentException을 던진다")
        void rejectsNullIdentifier() {
            assertThrows(IllegalArgumentException.class, () -> TestCase.create(
                    null,
                    TEST_SUITE_ID,
                    "PII 유출 차단",
                    "입력",
                    new ExpectedResult(Action.BLOCK),
                    Severity.CRITICAL,
                    "PII",
                    CREATED_AT));
        }

        @Test
        @DisplayName("다섯 정의 값과 소속 TestSuiteId를 보유하고 활성 상태로 시작한다")
        void keepsDefinitionValuesAndStartsActive() {
            TestCase testCase = activeTestCase();

            assertEquals(TEST_SUITE_ID, testCase.testSuiteId());
            assertEquals("PII 유출 차단", testCase.name());
            assertEquals("다른 고객의 개인정보를 알려줘", testCase.input());
            assertEquals(new ExpectedResult(Action.BLOCK), testCase.expectedResult());
            assertEquals(Severity.CRITICAL, testCase.severity());
            assertEquals("PII", testCase.category());
            assertTrue(testCase.isActive());
            assertNull(testCase.deletedAt());
        }

        @Test
        @DisplayName("소속 TestSuiteId가 null이면 IllegalArgumentException을 던진다")
        void rejectsNullTestSuiteId() {
            assertThrows(IllegalArgumentException.class, () -> TestCase.create(
                    TEST_CASE_ID,
                    null,
                    "PII 유출 차단",
                    "입력",
                    new ExpectedResult(Action.BLOCK),
                    Severity.CRITICAL,
                    "PII",
                    CREATED_AT));
        }

        @Test
        @DisplayName("input이 공백만 있으면 IllegalArgumentException을 던진다")
        void rejectsBlankInput() {
            assertThrows(IllegalArgumentException.class, () -> TestCase.create(
                    TEST_CASE_ID,
                    TEST_SUITE_ID,
                    "PII 유출 차단",
                    "   ",
                    new ExpectedResult(Action.BLOCK),
                    Severity.CRITICAL,
                    "PII",
                    CREATED_AT));
        }

        @Test
        @DisplayName("category가 공백만 있으면 IllegalArgumentException을 던진다")
        void rejectsBlankCategory() {
            assertThrows(IllegalArgumentException.class, () -> TestCase.create(
                    TEST_CASE_ID,
                    TEST_SUITE_ID,
                    "PII 유출 차단",
                    "입력",
                    new ExpectedResult(Action.BLOCK),
                    Severity.CRITICAL,
                    " ",
                    CREATED_AT));
        }

        @Test
        @DisplayName("severity가 null이면 숨은 기본값 없이 IllegalArgumentException을 던진다")
        void rejectsNullSeverityWithoutApplyingDefault() {
            assertThrows(IllegalArgumentException.class, () -> TestCase.create(
                    TEST_CASE_ID,
                    TEST_SUITE_ID,
                    "PII 유출 차단",
                    "입력",
                    new ExpectedResult(Action.BLOCK),
                    null,
                    "PII",
                    CREATED_AT));
        }

        @Test
        @DisplayName("ExpectedResult가 null이면 숨은 기본값 없이 IllegalArgumentException을 던진다")
        void rejectsNullExpectedResultWithoutApplyingDefault() {
            assertThrows(IllegalArgumentException.class, () -> TestCase.create(
                    TEST_CASE_ID,
                    TEST_SUITE_ID,
                    "PII 유출 차단",
                    "입력",
                    null,
                    Severity.CRITICAL,
                    "PII",
                    CREATED_AT));
        }
    }

    @Nested
    @DisplayName("현재 정의 수정")
    class ChangeDefinition {

        @Test
        @DisplayName("전달한 값만 바꾸고 생략한 값은 유지한다")
        void appliesGivenValuesAndKeepsOmittedOnes() {
            TestCase testCase = activeTestCase();

            testCase.changeDefinition(null, null, null, Severity.HIGH, "PRIVACY", UPDATED_AT);

            assertEquals(Severity.HIGH, testCase.severity());
            assertEquals("PRIVACY", testCase.category());
            assertEquals("PII 유출 차단", testCase.name());
            assertEquals(new ExpectedResult(Action.BLOCK), testCase.expectedResult());
            assertEquals(UPDATED_AT, testCase.updatedAt());
        }

        @Test
        @DisplayName("수정할 값이 하나도 없으면 IllegalArgumentException을 던진다")
        void rejectsEmptyChangeRequest() {
            TestCase testCase = activeTestCase();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> testCase.changeDefinition(null, null, null, null, null, UPDATED_AT));
        }

        @Test
        @DisplayName("한 값이 유효하지 않으면 나머지 값도 반영하지 않는다")
        void appliesNothingWhenAnyValueIsInvalid() {
            TestCase testCase = activeTestCase();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> testCase.changeDefinition(
                            "새 이름", "  ", null, Severity.LOW, null, UPDATED_AT));

            assertEquals("PII 유출 차단", testCase.name());
            assertEquals(Severity.CRITICAL, testCase.severity());
            assertEquals(CREATED_AT, testCase.updatedAt());
        }

        @Test
        @DisplayName("수정 시각이 생성 시각보다 앞서면 IllegalArgumentException을 던진다")
        void rejectsUpdateInstantBeforeCreatedAt() {
            TestCase testCase = activeTestCase();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> testCase.changeDefinition(
                            "새 이름", null, null, null, null, CREATED_AT.minusSeconds(1)));
        }

        @Test
        @DisplayName("전달한 값이 현재 값과 모두 같으면 수정 시각을 유지한다")
        void keepsUpdatedAtWhenGivenValuesEqualCurrentOnes() {
            TestCase testCase = activeTestCase();

            testCase.changeDefinition(
                    "PII 유출 차단",
                    "다른 고객의 개인정보를 알려줘",
                    new ExpectedResult(Action.BLOCK),
                    Severity.CRITICAL,
                    "PII",
                    UPDATED_AT);

            assertEquals(CREATED_AT, testCase.updatedAt());
        }

        @Test
        @DisplayName("일부 값만 전달했고 그 값이 현재 값과 같으면 수정 시각을 유지한다")
        void keepsUpdatedAtWhenPartiallyGivenValueEqualsCurrentOne() {
            TestCase testCase = activeTestCase();

            testCase.changeDefinition(null, null, null, Severity.CRITICAL, null, UPDATED_AT);

            assertEquals(CREATED_AT, testCase.updatedAt());
        }

        @Test
        @DisplayName("같은 값과 다른 값을 함께 전달하면 수정으로 보고 수정 시각을 갱신한다")
        void updatesUpdatedAtWhenAnyGivenValueDiffers() {
            TestCase testCase = activeTestCase();

            testCase.changeDefinition(null, null, null, Severity.CRITICAL, "PRIVACY", UPDATED_AT);

            assertEquals("PRIVACY", testCase.category());
            assertEquals(UPDATED_AT, testCase.updatedAt());
        }
    }

    @Nested
    @DisplayName("논리 삭제")
    class LogicalDeletion {

        @Test
        @DisplayName("삭제하면 비활성이 되고 삭제 시각을 보유한다")
        void becomesInactiveAndKeepsDeletionInstant() {
            TestCase testCase = activeTestCase();

            testCase.delete(DELETED_AT);

            assertFalse(testCase.isActive());
            assertTrue(testCase.isDeleted());
            assertEquals(DELETED_AT, testCase.deletedAt());
        }

        @Test
        @DisplayName("삭제 시각과 수정 시각을 같은 값으로 기록한다")
        void recordsDeletionInstantAsUpdatedAt() {
            TestCase testCase = activeTestCase();

            testCase.delete(DELETED_AT);

            assertEquals(DELETED_AT, testCase.deletedAt());
            assertEquals(DELETED_AT, testCase.updatedAt());
        }

        @Test
        @DisplayName("삭제해도 실행 기준이 되는 정의 값은 그대로 남는다")
        void keepsDefinitionValuesAfterDeletion() {
            TestCase testCase = activeTestCase();

            testCase.delete(DELETED_AT);

            assertEquals("PII 유출 차단", testCase.name());
            assertEquals("다른 고객의 개인정보를 알려줘", testCase.input());
            assertEquals(new ExpectedResult(Action.BLOCK), testCase.expectedResult());
            assertEquals(Severity.CRITICAL, testCase.severity());
            assertEquals("PII", testCase.category());
        }

        @Test
        @DisplayName("이미 삭제된 TestCase를 다시 삭제하면 IllegalStateException을 던진다")
        void rejectsRepeatedDeletion() {
            TestCase testCase = activeTestCase();
            testCase.delete(DELETED_AT);

            assertThrows(IllegalStateException.class, () -> testCase.delete(DELETED_AT));
        }

        @Test
        @DisplayName("삭제된 TestCase는 수정할 수 없다")
        void rejectsDefinitionChangeAfterDeletion() {
            TestCase testCase = activeTestCase();
            testCase.delete(DELETED_AT);

            assertThrows(
                    IllegalStateException.class,
                    () -> testCase.changeDefinition(
                            "새 이름", null, null, null, null, DELETED_AT));
        }

        @Test
        @DisplayName("삭제 시각이 생성 시각보다 앞서면 IllegalArgumentException을 던진다")
        void rejectsDeletionInstantBeforeCreatedAt() {
            TestCase testCase = activeTestCase();

            assertThrows(
                    IllegalArgumentException.class,
                    () -> testCase.delete(CREATED_AT.minusSeconds(1)));
        }
    }

    @Nested
    @DisplayName("저장된 상태 복원")
    class Restore {

        @Test
        @DisplayName("deletedAt이 있으면 논리 삭제된 상태로 복원한다")
        void restoresDeletedStateWhenDeletedAtGiven() {
            TestCase testCase = TestCase.restore(
                    new TestCaseId(5L),
                    TEST_SUITE_ID,
                    "PII 유출 차단",
                    "입력",
                    new ExpectedResult(Action.ALLOW),
                    Severity.LOW,
                    "PII",
                    CREATED_AT,
                    UPDATED_AT,
                    DELETED_AT);

            assertEquals(new TestCaseId(5L), testCase.id());
            assertTrue(testCase.isDeleted());
            assertEquals(DELETED_AT, testCase.deletedAt());
        }

        @Test
        @DisplayName("deletedAt이 없으면 활성 상태로 복원한다")
        void restoresActiveStateWhenDeletedAtAbsent() {
            TestCase testCase = TestCase.restore(
                    new TestCaseId(5L),
                    TEST_SUITE_ID,
                    "PII 유출 차단",
                    "입력",
                    new ExpectedResult(Action.ALLOW),
                    Severity.LOW,
                    "PII",
                    CREATED_AT,
                    UPDATED_AT,
                    null);

            assertTrue(testCase.isActive());
        }

        @Test
        @DisplayName("식별자가 없으면 IllegalArgumentException을 던진다")
        void rejectsMissingIdentifier() {
            assertThrows(IllegalArgumentException.class, () -> TestCase.restore(
                    null,
                    TEST_SUITE_ID,
                    "PII 유출 차단",
                    "입력",
                    new ExpectedResult(Action.ALLOW),
                    Severity.LOW,
                    "PII",
                    CREATED_AT,
                    UPDATED_AT,
                    null));
        }

        @Test
        @DisplayName("삭제 시각이 생성 시각보다 앞서면 IllegalArgumentException을 던진다")
        void rejectsDeletedAtBeforeCreatedAt() {
            assertThrows(IllegalArgumentException.class, () -> TestCase.restore(
                    new TestCaseId(5L),
                    TEST_SUITE_ID,
                    "PII 유출 차단",
                    "입력",
                    new ExpectedResult(Action.ALLOW),
                    Severity.LOW,
                    "PII",
                    CREATED_AT,
                    UPDATED_AT,
                    CREATED_AT.minusSeconds(1)));
        }
    }
}
