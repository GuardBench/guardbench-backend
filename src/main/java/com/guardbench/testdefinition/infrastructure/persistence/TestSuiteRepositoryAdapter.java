package com.guardbench.testdefinition.infrastructure.persistence;

import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.guardbench.testdefinition.domain.TestSuite;
import com.guardbench.testdefinition.domain.TestSuiteId;
import com.guardbench.testdefinition.domain.repository.TestSuiteRepository;

/**
 * Domain의 {@link TestSuiteRepository}를 PostgreSQL로 구현한다.
 *
 * <p>Spring Data와 JPA 타입은 이 클래스와 {@link TestSuiteJpaRepository}에서 끝나고 Domain으로
 * 올라가지 않는다. 반환 값은 항상 Domain Aggregate로 변환한다.
 *
 * <p>{@link #save(TestSuite)}는 Spring Data의 {@code save}를 사용한다. 식별자가 저장 전에 이미
 * 부여되므로 Spring Data가 이 Entity를 새 Entity로 판단하지 않고 {@code merge}를 호출한다. 그래서 신규
 * 저장에도 INSERT 앞에 SELECT가 한 번 선행된다. 이를 없애려면 Domain Port에 신규와 갱신을 구분하는
 * 계약을 두거나 스키마에 version 컬럼을 추가해야 하는데, 둘 다 이 Issue의 범위를 벗어나므로 MVP에서는
 * 이 비용을 받아들인다.
 *
 * <p>근거: {@code docs/decisions/0002-postgresql-persistence-contract.md},
 * {@code docs/conventions/package-structure.md}
 */
@Repository
class TestSuiteRepositoryAdapter implements TestSuiteRepository {

    private final TestSuiteJpaRepository jpaRepository;

    TestSuiteRepositoryAdapter(TestSuiteJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public TestSuiteId nextIdentity() {
        return new TestSuiteId(jpaRepository.nextSequenceValue());
    }

    @Override
    public TestSuite save(TestSuite testSuite) {
        Objects.requireNonNull(testSuite, "TestSuite must not be null");

        TestSuiteEntity saved = jpaRepository.save(TestSuiteEntityMapper.toEntity(testSuite));

        return TestSuiteEntityMapper.toDomain(saved);
    }

    @Override
    public Optional<TestSuite> findById(TestSuiteId id) {
        Objects.requireNonNull(id, "TestSuiteId must not be null");

        return jpaRepository.findById(id.value()).map(TestSuiteEntityMapper::toDomain);
    }

    @Override
    public boolean existsById(TestSuiteId id) {
        Objects.requireNonNull(id, "TestSuiteId must not be null");

        return jpaRepository.existsById(id.value());
    }
}
