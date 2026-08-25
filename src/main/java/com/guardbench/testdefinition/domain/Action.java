package com.guardbench.testdefinition.domain;

/**
 * Guardrail이 입력에 대해 취하는 판정 action이다.
 *
 * <p>`testdefinition`이 소유하는 단일 타입이며 `ExpectedResult`와 실행 결과가 같은 값 집합을 사용한다.
 * 다른 도메인이나 {@code common}에 같은 의미의 enum을 다시 만들지 않는다.
 *
 * <p>값 집합은 승인된 계약과 물리 스키마의 {@code ck_test_case_expected_action} 제약이 함께 고정한다.
 * 값 추가나 이름 변경은 공개 API와 DB 제약을 동시에 바꾸는 계약 변경으로 다룬다.
 *
 * <p>근거: {@code docs/domain/evaluation-contract.md}, {@code docs/decisions/0001-domain-type-ownership-and-aggregate-boundaries.md}
 */
public enum Action {

    ALLOW,
    BLOCK
}
