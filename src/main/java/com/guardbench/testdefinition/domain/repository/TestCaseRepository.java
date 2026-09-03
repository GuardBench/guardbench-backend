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
 * <p>TestCase 삭제는 물리 삭제로 수행하며 과거 실행 Snapshot은 별도로 보존한다. 페이지 조회와
 * 검색·필터·정렬이 필요한 목록 조회는 이 Port가 아니라
 * Application 경계의 조회 전용 계약에 둔다.
 *
 * <p>근거: {@code docs/decisions/0001-domain-type-ownership-and-aggregate-boundaries.md},
 * {@code docs/domain/core-model.md}, {@code docs/conventions/package-structure.md}
 */
public interface TestCaseRepository {

    /**
     * 저장 전에 사용할 새 식별자를 발급한다.
     *
     * <p>Aggregate가 식별자 없는 상태를 갖지 않도록 Application이 {@code TestCase.create}를 호출하기
     * 전에 이 method로 식별자를 얻는다.
     *
     * <p>구현은 물리 스키마의 {@code test_case_id_seq}에서 값을 얻는다. 시퀀스가
     * {@code INCREMENT BY 50}이므로 구현이 값을 묶어 받아 DB 왕복을 줄일 수 있다. 발급된 식별자를
     * 저장에 사용하지 않아도 무결성 문제가 생기지 않는다.
     */
    TestCaseId nextIdentity();

    /**
     * 새 TestCase를 저장하거나 기존 TestCase의 변경을 반영한다.
     */
    TestCase save(TestCase testCase);

    /**
     * 여러 TestCase를 함께 저장한다. TestSuite와 초기 TestCase를 한 트랜잭션으로 만드는 유스케이스가
     * 사용한다.
     */
    List<TestCase> saveAll(List<TestCase> testCases);

    /**
     * 삭제되지 않은 TestCase만 식별자로 조회한다.
     */
    Optional<TestCase> findById(TestCaseId id);

    /**
     * TestSuite에 현재 소속된 TestCase를 모두 조회한다. TestRun 접수 시점의 실행 대상 집합을
     * 만드는 데 사용한다.
     */
    List<TestCase> findByTestSuiteId(TestSuiteId testSuiteId);

    /**
     * TestSuite에 소속된 TestCase 수를 센다.
     */
    long countByTestSuiteId(TestSuiteId testSuiteId);

    /** TestSuite 삭제 전에 해당 Suite의 현재 TestCase를 모두 물리 삭제한다. */
    void deleteAllByTestSuiteId(TestSuiteId testSuiteId);

    /** TestCase를 물리 삭제한다. */
    void deleteById(TestCaseId id);
}
