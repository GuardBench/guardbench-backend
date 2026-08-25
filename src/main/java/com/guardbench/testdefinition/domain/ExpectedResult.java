package com.guardbench.testdefinition.domain;

/**
 * TestCase가 기대하는 Guardrail 판정이다.
 *
 * <p>사용자가 작성하는 현재 TestCase 정의의 일부이며 불변 Value Object다. MVP evaluator는 action만
 * 판정에 사용하므로 이 타입도 action만 보유한다.
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
