package com.guardbench.testdefinition.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UnicodeBlankValidationTest {

    private static final Instant NOW = Instant.parse("2026-08-25T10:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"\u00A0", "\u2007", "\u202F", "\uFEFF", " ", "\t", "\n", "\u3000"})
    @DisplayName("Unicode 공백만 있는 TestDefinition 필수 문자열은 모두 거부한다")
    void rejectsWhitespaceOnlyRequiredStrings(String whitespace) {
        assertThrows(IllegalArgumentException.class,
                () -> TestSuite.create(new TestSuiteId(1L), whitespace, null, NOW));
        assertThrows(IllegalArgumentException.class,
                () -> testCase(whitespace, "input", "category"));
        assertThrows(IllegalArgumentException.class,
                () -> testCase("name", whitespace, "category"));
        assertThrows(IllegalArgumentException.class,
                () -> testCase("name", "input", whitespace));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\u00A0", "\u2007", "\u202F", "\uFEFF", " ", "\t", "\n", "\u3000"})
    @DisplayName("Unicode 공백만 있는 선택 설명은 null로 정규화한다")
    void normalizesWhitespaceOnlyDescriptionToNull(String whitespace) {
        TestSuite testSuite = TestSuite.create(new TestSuiteId(1L), "name", whitespace, NOW);

        assertNull(testSuite.description());
    }

    @ParameterizedTest
    @ValueSource(strings = {"개인정보\u00A0차단", "개인정보\u2007차단", "개인정보\u202F차단", "개인정보\uFEFF차단"})
    @DisplayName("정상 문자 사이에 Unicode 공백이 있는 TestDefinition 문자열은 허용한다")
    void acceptsUnicodeWhitespaceBetweenNonWhitespaceCharacters(String value) {
        assertDoesNotThrow(() -> TestSuite.create(new TestSuiteId(1L), value, value, NOW));
        assertDoesNotThrow(() -> testCase(value, value, value));
    }

    private static TestCase testCase(String name, String input, String category) {
        return TestCase.create(
                new TestCaseId(1L),
                new TestSuiteId(1L),
                name,
                input,
                new ExpectedResult(Action.BLOCK),
                Severity.CRITICAL,
                category,
                NOW);
    }
}
