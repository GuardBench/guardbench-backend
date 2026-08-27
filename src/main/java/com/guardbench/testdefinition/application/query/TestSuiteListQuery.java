package com.guardbench.testdefinition.application.query;

/**
 * TestSuite 목록 조회 계약이다.
 *
 * <p>Aggregate 저장 계약이 아니다. 승인된 경계가 페이지 조회와 화면용 Projection을 Aggregate
 * Repository에 두지 않고 Application 경계의 조회 전용 Port로 두도록 정하므로, 이 Port를 쓰기 경로로
 * 사용하지 않는다. 저장과 단건 조회는 {@code domain.repository.TestSuiteRepository}가 담당한다.
 *
 * <p>{@code testdefinition/infrastructure/persistence}가 구현한다. 구현은 filter와 정렬을 전체 결과에
 * 적용한 뒤 {@code LIMIT}과 {@code OFFSET}으로 잘라내고 전체 건수는 별도 count query로 얻는다.
 *
 * <p>근거: {@code docs/decisions/0001-domain-type-ownership-and-aggregate-boundaries.md},
 * {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
public interface TestSuiteListQuery {

    PageResult<TestSuiteSummary> find(TestSuiteListCriteria criteria);
}
