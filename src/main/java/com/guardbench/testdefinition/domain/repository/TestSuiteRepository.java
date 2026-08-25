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
     * 새 TestSuite를 저장하거나 기존 TestSuite의 변경을 반영하고 식별자가 부여된 Aggregate를 돌려준다.
     */
    TestSuite save(TestSuite testSuite);

    Optional<TestSuite> findById(TestSuiteId id);

    boolean existsById(TestSuiteId id);
}
