package com.guardbench.testdefinition.application.query;

/**
 * TestSuite 하나에 속한 활성 TestCase 목록 조회 계약이다.
 *
 * <p>Aggregate 저장 계약이 아니다. 승인된 경계가 페이지 조회를 Aggregate Repository에 두지 않도록
 * 정하므로 이 Port를 쓰기 경로로 사용하지 않는다. 저장과 단건 조회는
 * {@code domain.repository.TestCaseRepository}가 담당한다.
 *
 * <p>method 이름이 활성 행만 다룬다는 것을 드러낸다. 논리 삭제된 TestCase는 조회 조건과 무관하게 결과에
 * 포함되지 않으며, 그 조건은 호출자가 지정하는 filter가 아니라 이 계약이 보장한다.
 *
 * <p>{@code testdefinition/infrastructure/persistence}가 구현한다.
 *
 * <p>근거: {@code docs/decisions/0001-domain-type-ownership-and-aggregate-boundaries.md},
 * {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
public interface TestCaseListQuery {

    PageResult<TestCaseSummary> findActive(TestCaseListCriteria criteria);
}
