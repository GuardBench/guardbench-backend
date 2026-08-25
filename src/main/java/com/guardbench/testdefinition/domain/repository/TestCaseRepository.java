package com.guardbench.testdefinition.domain.repository;

import java.util.List;
import java.util.Optional;

import com.guardbench.testdefinition.domain.TestCase;
import com.guardbench.testdefinition.domain.TestCaseId;
import com.guardbench.testdefinition.domain.TestSuiteId;

/**
 * TestCase Aggregate의 저장 계약이다.
 *
 * <p>Domain이 정의하고 {@code testdefinition/infrastructure/persistence}가 구현한다. Domain은 JPA와
 * Spring Data 타입을 알지 않는다.
 *
 * <p>삭제는 논리 삭제이므로 물리 삭제 method를 두지 않는다. 삭제는 Aggregate의 상태 변경을
 * {@link #save(TestCase)}로 반영해 수행한다.
 *
 * <p>조회 method는 활성 TestCase만 다루는지 삭제된 것까지 포함하는지를 이름으로 구분한다. 페이지
 * 조회와 검색·필터·정렬이 필요한 목록 조회는 이 Port가 아니라 Application 경계의 조회 전용 계약에
 * 둔다.
 *
 * <p>근거: {@code docs/decisions/0001-domain-type-ownership-and-aggregate-boundaries.md},
 * {@code docs/domain/core-model.md}, {@code docs/conventions/package-structure.md}
 */
public interface TestCaseRepository {

    /**
     * 새 TestCase를 저장하거나 기존 TestCase의 변경을 반영하고 식별자가 부여된 Aggregate를 돌려준다.
     */
    TestCase save(TestCase testCase);

    /**
     * 여러 TestCase를 함께 저장한다. TestSuite와 초기 TestCase를 한 트랜잭션으로 만드는 유스케이스가
     * 사용한다.
     */
    List<TestCase> saveAll(List<TestCase> testCases);

    /**
     * 논리 삭제 여부와 무관하게 식별자로 조회한다. 삭제된 TestCase의 재삭제와 삭제 후 수정을 계약대로
     * 거부하려면 삭제 상태를 알아야 하므로 필터링하지 않는다.
     */
    Optional<TestCase> findById(TestCaseId id);

    /**
     * 삭제되지 않은 TestCase만 식별자로 조회한다.
     */
    Optional<TestCase> findActiveById(TestCaseId id);

    /**
     * TestSuite에 현재 소속된 활성 TestCase를 모두 조회한다. TestRun 접수 시점의 실행 대상 집합을
     * 만드는 데 사용한다.
     */
    List<TestCase> findActiveByTestSuiteId(TestSuiteId testSuiteId);

    /**
     * TestSuite에 현재 소속된 활성 TestCase 수를 센다.
     */
    long countActiveByTestSuiteId(TestSuiteId testSuiteId);
}
