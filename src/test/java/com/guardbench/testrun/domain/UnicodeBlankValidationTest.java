package com.guardbench.testrun.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.guardbench.testrun.application.port.out.TestCaseSnapshotSource;

class UnicodeBlankValidationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T10:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"\u00A0", "\u2007", "\u202F", "\uFEFF", " ", "\t", "\n", "\u3000"})
    @DisplayName("Unicode 공백만 있는 TestRun 필수 문자열은 모두 거부한다")
    void rejectsWhitespaceOnlyRequiredStrings(String whitespace) {
        assertThrows(IllegalArgumentException.class,
                () -> new BaselineTarget(whitespace, "1"));
        assertThrows(IllegalArgumentException.class,
                () -> new CandidateTarget(whitespace, CandidateSource.DRAFT, null));
        assertThrows(IllegalArgumentException.class,
                () -> new TestExecutionError(TestExecutionErrorCode.PROVIDER_TIMEOUT, whitespace));
        assertThrows(IllegalArgumentException.class,
                () -> snapshot(whitespace, "input", "category"));
        assertThrows(IllegalArgumentException.class,
                () -> snapshot("name", whitespace, "category"));
        assertThrows(IllegalArgumentException.class,
                () -> snapshot("name", "input", whitespace));
        assertSourceRejects(whitespace);
    }

    @ParameterizedTest
    @ValueSource(strings = {"개인정보\u00A0차단", "개인정보\u2007차단", "개인정보\u202F차단", "개인정보\uFEFF차단"})
    @DisplayName("정상 문자 사이에 Unicode 공백이 있는 TestRun 문자열은 허용한다")
    void acceptsUnicodeWhitespaceBetweenNonWhitespaceCharacters(String value) {
        assertDoesNotThrow(() -> new BaselineTarget(value, "1"));
        assertDoesNotThrow(() -> new CandidateTarget(value, CandidateSource.DRAFT, null));
        assertDoesNotThrow(() -> new TestExecutionError(TestExecutionErrorCode.PROVIDER_TIMEOUT, value));
        assertDoesNotThrow(() -> snapshot(value, value, value));
        assertDoesNotThrow(() -> source(value, value, value, value, value));
    }

    private static void assertSourceRejects(String whitespace) {
        assertThrows(IllegalArgumentException.class,
                () -> source(whitespace, "input", "ALLOW", "HIGH", "category"));
        assertThrows(IllegalArgumentException.class,
                () -> source("name", whitespace, "ALLOW", "HIGH", "category"));
        assertThrows(IllegalArgumentException.class,
                () -> source("name", "input", whitespace, "HIGH", "category"));
        assertThrows(IllegalArgumentException.class,
                () -> source("name", "input", "ALLOW", whitespace, "category"));
        assertThrows(IllegalArgumentException.class,
                () -> source("name", "input", "ALLOW", "HIGH", whitespace));
    }

    private static TestCaseSnapshot snapshot(String name, String input, String category) {
        return TestCaseSnapshot.of(
                new TestCaseSnapshotId(1L),
                new TestRunId(1L),
                new SourceTestCaseId(1L),
                name,
                input,
                new ExpectedResult(Action.BLOCK),
                Severity.CRITICAL,
                category,
                CREATED_AT);
    }

    private static TestCaseSnapshotSource source(
            String name,
            String input,
            String expectedActionCode,
            String severityCode,
            String category) {
        return new TestCaseSnapshotSource(
                1L,
                1L,
                name,
                input,
                expectedActionCode,
                severityCode,
                category);
    }
}
