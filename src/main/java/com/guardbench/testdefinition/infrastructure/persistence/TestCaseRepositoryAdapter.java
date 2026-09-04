package com.guardbench.testdefinition.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testdefinition.domain.TestCase;
import com.guardbench.testdefinition.domain.TestCaseId;
import com.guardbench.testdefinition.domain.TestSuiteId;
import com.guardbench.testdefinition.domain.repository.TestCaseRepository;

import jakarta.persistence.EntityManager;

/**
 * Domain의 {@link TestCaseRepository}를 PostgreSQL로 구현한다.
 *
 * <p>Spring Data와 JPA 타입은 이 클래스와 {@link TestCaseJpaRepository}에서 끝나고 Domain으로
 * 올라가지 않는다.
 *
 * <h2>saveAll의 신규·기존 구분</h2>
 *
 * <p>식별자가 저장 전에 부여되므로 Spring Data는 모든 Entity를 기존 행으로 판단해 {@code merge}를
 * 호출하고, 신규 저장에도 INSERT 앞에 SELECT가 하나씩 붙는다. 승인된 API 계약이 TestSuite 생성 시 초기
 * TestCase를 최대 1000개까지 허용하므로 배치에서는 이 비용이 건수만큼 늘어난다.
 *
 * <p>그래서 {@code saveAll}은 식별자 전체를 한 번의 query로 확인해 이미 저장된 것만 가려낸 뒤, 신규는
 * {@code persist}로 SELECT 없이 넣고 기존은 {@code merge}로 반영한다. 배치 크기와 무관하게 확인 query가
 * 하나다. 신규와 기존이 섞인 목록도 정상 처리한다.
 *
 * <p>단건 저장과 삭제는 Spring Data의 {@code save}/{@code deleteById}를 사용한다. 과거 실행 데이터는
 * TestCase와 별도 Aggregate이므로 TestCase 삭제가 전파되지 않는다.
 *
 * <p>근거: {@code docs/decisions/0002-postgresql-persistence-contract.md},
 * {@code docs/api/openapi.yaml},
 * {@code docs/decisions/0012-testdefinition-hard-delete-and-historical-identity.md}
 */
@Repository
class TestCaseRepositoryAdapter implements TestCaseRepository {

    private final TestCaseJpaRepository jpaRepository;
    private final EntityManager entityManager;

    TestCaseRepositoryAdapter(TestCaseJpaRepository jpaRepository, EntityManager entityManager) {
        this.jpaRepository = jpaRepository;
        this.entityManager = entityManager;
    }

    @Override
    public TestCaseId nextIdentity() {
        return new TestCaseId(jpaRepository.nextSequenceValue());
    }

    @Override
    @Transactional
    public TestCase save(TestCase testCase) {
        Objects.requireNonNull(testCase, "TestCase must not be null");

        TestCaseEntity saved = jpaRepository.save(TestCaseEntityMapper.toEntity(testCase));

        return TestCaseEntityMapper.toDomain(saved);
    }

    @Override
    @Transactional
    public List<TestCase> saveAll(List<TestCase> testCases) {
        Objects.requireNonNull(testCases, "TestCase list must not be null");

        if (testCases.isEmpty()) {
            return List.of();
        }

        List<TestCaseEntity> entities = testCases.stream()
                .map(TestCaseEntityMapper::toEntity)
                .toList();
        Set<Long> storedIds = findStoredIds(entities);

        List<TestCase> saved = new ArrayList<>(entities.size());
        for (TestCaseEntity entity : entities) {
            saved.add(TestCaseEntityMapper.toDomain(store(entity, storedIds)));
        }

        return List.copyOf(saved);
    }

    @Override
    public Optional<TestCase> findById(TestCaseId id) {
        Objects.requireNonNull(id, "TestCaseId must not be null");

        return jpaRepository.findById(id.value())
                .map(TestCaseEntityMapper::toDomain);
    }

    @Override
    public List<TestCase> findByTestSuiteId(TestSuiteId testSuiteId) {
        Objects.requireNonNull(testSuiteId, "TestSuiteId must not be null");

        return jpaRepository
                .findByTestSuiteIdOrderByCreatedAtAscIdAsc(testSuiteId.value())
                .stream()
                .map(TestCaseEntityMapper::toDomain)
                .toList();
    }

    @Override
    public long countByTestSuiteId(TestSuiteId testSuiteId) {
        Objects.requireNonNull(testSuiteId, "TestSuiteId must not be null");

        return jpaRepository.countByTestSuiteId(testSuiteId.value());
    }

    @Override
    public void deleteAllByTestSuiteId(TestSuiteId testSuiteId) {
        Objects.requireNonNull(testSuiteId, "TestSuiteId must not be null");
        jpaRepository.deleteByTestSuiteId(testSuiteId.value());
        entityManager.flush();
    }

    @Override
    public void deleteById(TestCaseId id) {
        Objects.requireNonNull(id, "TestCaseId must not be null");
        jpaRepository.deleteById(id.value());
    }

    private Set<Long> findStoredIds(List<TestCaseEntity> entities) {
        List<Long> ids = entities.stream().map(TestCaseEntity::id).toList();

        return Set.copyOf(jpaRepository.findExistingIds(ids));
    }

    private TestCaseEntity store(TestCaseEntity entity, Set<Long> storedIds) {
        if (storedIds.contains(entity.id())) {
            return entityManager.merge(entity);
        }

        entityManager.persist(entity);

        return entity;
    }

}
