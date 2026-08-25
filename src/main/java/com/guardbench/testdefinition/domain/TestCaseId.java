package com.guardbench.testdefinition.domain;

/**
 * TestCase Aggregate의 전용 식별자다.
 *
 * <p>{@code TestSuite}와 {@code TestCase}는 별도 Aggregate Root이므로 각각 전용 식별자를 갖는다.
 * 원시 숫자 대신 전용 타입을 두어 두 식별자를 뒤바꿔 전달하는 실수를 컴파일 시점에 막는다.
 *
 * <p>다른 Context는 이 타입을 import하지 않는다. TestRun Snapshot은 원본 TestCase를 자신이 소유한
 * reference 타입으로 표현한다.
 *
 * <p>값은 물리 스키마의 {@code test_case.id}에 대응하는 양의 정수다.
 *
 * <p>근거: {@code docs/decisions/0001-domain-type-ownership-and-aggregate-boundaries.md},
 * {@code docs/decisions/0006-independent-domain-contract-boundaries.md}
 */
public record TestCaseId(long value) {

    public TestCaseId {
        if (value <= 0) {
            throw new IllegalArgumentException("TestCaseId는 양수여야 합니다. value=" + value);
        }
    }
}
