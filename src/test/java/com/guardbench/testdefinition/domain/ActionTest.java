package com.guardbench.testdefinition.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code Action}의 값 집합이 승인 계약과 일치하는지 검증한다.
 *
 * <p>근거: {@code docs/domain/evaluation-contract.md}의 {@code ExpectedResult.action}과
 * {@code src/main/resources/db/migration/V1__create_guardbench_schema.sql}의
 * {@code ck_test_case_expected_action} 제약.
 */
class ActionTest {

    @Test
    @DisplayName("승인 계약과 DB 제약이 정한 ALLOW, BLOCK 두 값만 정의한다")
    void definesOnlyApprovedActionValues() {
        Action[] expected = {Action.ALLOW, Action.BLOCK};

        assertArrayEquals(expected, Action.values());
    }
}
