package com.guardbench.testdefinition.domain;

/**
 * TestSuite Aggregate의 전용 식별자다.
 *
 * <p>원시 숫자 대신 전용 타입을 두어 서로 다른 Aggregate의 식별자를 뒤바꿔 전달하는 실수를 컴파일
 * 시점에 막는다. 공통 ID 상위 타입은 만들지 않는다.
 *
 * <p>같은 Bounded Context 안에서는 {@code TestCase}가 이 타입으로 소속을 가리킨다. 다른 Context는
 * 이 타입을 import하지 않고 자신이 소유한 reference 타입과 scalar 값으로 원본을 표현한다.
 *
 * <p>값은 물리 스키마의 {@code test_suite.id}에 대응하는 양의 정수다.
 *
 * <p>근거: {@code docs/decisions/0001-domain-type-ownership-and-aggregate-boundaries.md},
 * {@code docs/decisions/0006-independent-domain-contract-boundaries.md}
 */
public record TestSuiteId(long value) {

    public TestSuiteId {
        if (value <= 0) {
            throw new IllegalArgumentException("TestSuiteId는 양수여야 합니다. value=" + value);
        }
    }
}
