package com.guardbench.testdefinition.domain;

/**
 * TestCase가 AI Application 응답에 대해 기대하는 평가 결과다.
 *
 * <p>사용자가 작성하는 현재 TestCase 정의의 일부이며 불변 Value Object다. MVP evaluator는 Application
 * 응답의 행동을 {@link Action}으로 정규화하므로 이 타입도 기대 action만 보유한다. 특정 Guardrail provider의
 * 판정 타입이 아니다.
 *
 * <p>TestRun의 Snapshot은 이 값을 자신이 소유한 타입으로 복제해 보존한다. 다른 Bounded Context가 이
 * 타입을 직접 import하지 않는다.
 *
 * <p>근거: {@code docs/domain/evaluation-contract.md}, {@code docs/decisions/0006-independent-domain-contract-boundaries.md}
 */
public record ExpectedResult(Action action) {

    public ExpectedResult {
        if (action == null) {
            throw new IllegalArgumentException("ExpectedResult의 action은 필수입니다.");
        }
    }
}
