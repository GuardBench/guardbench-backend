package com.guardbench.testdefinition.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code Severity}의 값 집합과 선언 순서가 승인 계약과 일치하는지 검증한다.
 *
 * <p>근거: {@code docs/api/README.md}의 severity 허용 값과 오름차순 정렬 기준,
 * {@code src/main/resources/db/migration/V1__create_guardbench_schema.sql}의
 * {@code ck_test_case_severity} 제약.
 */
class SeverityTest {

    @Test
    @DisplayName("승인 계약의 네 값만 정의하고 선언 순서가 오름차순 LOW, MEDIUM, HIGH, CRITICAL과 같다")
    void definesApprovedSeverityValuesInAscendingOrder() {
        Severity[] expected = {Severity.LOW, Severity.MEDIUM, Severity.HIGH, Severity.CRITICAL};

        assertArrayEquals(expected, Severity.values());
    }
}
