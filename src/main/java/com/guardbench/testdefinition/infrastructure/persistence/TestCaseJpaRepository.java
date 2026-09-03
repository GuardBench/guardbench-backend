package com.guardbench.testdefinition.infrastructure.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@link TestCaseEntity}에 대한 Spring Data 접근 지점이다.
 *
 * <p>Domain Port는 {@link TestCaseRepositoryAdapter}가 이 인터페이스를 사용해 구현하며, Spring Data
 * 타입은 이 패키지 밖으로 나가지 않는다.
 *
 * <p>근거: {@code docs/decisions/0002-postgresql-persistence-contract.md},
 * {@code docs/conventions/package-structure.md}
 */
interface TestCaseJpaRepository extends JpaRepository<TestCaseEntity, Long> {

    /**
     * 물리 스키마의 {@code test_case_id_seq}에서 다음 값을 얻는다.
     *
     * <p>JPA 표준에는 시퀀스를 직접 읽는 방법이 없어 native query를 사용한다.
     */
    @Query(value = "SELECT nextval('test_case_id_seq')", nativeQuery = true)
    long nextSequenceValue();

    Optional<TestCaseEntity> findById(Long id);

    /**
     * TestSuite에 소속된 행을 승인된 기본 정렬 순서로 조회한다.
     */
    List<TestCaseEntity> findByTestSuiteIdOrderByCreatedAtAscIdAsc(
            Long testSuiteId);

    long countByTestSuiteId(Long testSuiteId);

    @Modifying
    @Query("delete from TestCaseEntity e where e.testSuiteId = :testSuiteId")
    void deleteByTestSuiteId(@Param("testSuiteId") Long testSuiteId);

    /**
     * 주어진 식별자 중 이미 저장된 것만 한 번의 query로 가려낸다.
     *
     * <p>{@code saveAll}이 신규와 기존을 구분하는 데 사용한다. 건마다 존재 여부를 확인하면 배치 크기
     * 만큼 query가 늘어나므로 한 번에 묶어 확인한다.
     */
    @Query("select e.id from TestCaseEntity e where e.id in :ids")
    List<Long> findExistingIds(@Param("ids") Collection<Long> ids);
}
