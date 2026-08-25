package com.guardbench.testdefinition.domain.repository;

import java.util.Optional;

import com.guardbench.testdefinition.domain.TestSuite;
import com.guardbench.testdefinition.domain.TestSuiteId;

/**
 * TestSuite Aggregate의 저장 계약이다.
 *
 * <p>Domain이 정의하고 {@code testdefinition/infrastructure/persistence}가 구현한다. Domain은 JPA와
 * Spring Data 타입을 알지 않는다.
 *
 * <p>{@code TestSuite}와 {@code TestCase}는 별도 Aggregate Root이므로 Port도 분리한다. 두 Aggregate를
 * 함께 저장해야 하는 유스케이스는 {@code testdefinition/application}이 두 Port를 한 트랜잭션에서
 * 호출해 조정한다.
 *
 * <p>페이지 조회와 화면용 Projection은 이 Port에 두지 않는다. 조회 전용 계약은 Application 경계에
 * 별도로 두며 이 Port를 조회 계층으로 사용하지 않는다.
 *
 * <p>근거: {@code docs/decisions/0001-domain-type-ownership-and-aggregate-boundaries.md},
 * {@code docs/conventions/package-structure.md}
 */
public interface TestSuiteRepository {

    /**
     * 저장 전에 사용할 새 식별자를 발급한다.
     *
     * <p>Aggregate가 식별자 없는 상태를 갖지 않도록 Application이 {@code TestSuite.create}를 호출하기
     * 전에 이 method로 식별자를 얻는다. 덕분에 TestSuite와 초기 TestCase를 저장 전에 메모리에서 모두
     * 조립할 수 있고 persistence flush 순서에 의존하지 않는다.
     *
     * <p>구현은 물리 스키마의 {@code test_suite_id_seq}에서 값을 얻는다. 시퀀스가
     * {@code INCREMENT BY 50}이므로 구현이 값을 묶어 받아 DB 왕복을 줄일 수 있다. 발급된 식별자를
     * 저장에 사용하지 않아도 무결성 문제가 생기지 않는다.
     */
    TestSuiteId nextIdentity();

    /**
     * 새 TestSuite를 저장하거나 기존 TestSuite의 변경을 반영한다.
     */
    TestSuite save(TestSuite testSuite);

    Optional<TestSuite> findById(TestSuiteId id);

    boolean existsById(TestSuiteId id);
}
