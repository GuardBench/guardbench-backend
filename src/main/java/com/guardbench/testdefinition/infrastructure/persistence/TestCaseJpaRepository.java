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
 * <p>활성 행만 다루는 method는 이름에 {@code DeletedAtIsNull}을 포함해 논리 삭제 조건을 명시한다.
 * 승인된 계약이 활성 행을 {@code deleted_at IS NULL}로 정의한다.
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

    Optional<TestCaseEntity> findByIdAndDeletedAtIsNull(Long id);

    /**
     * TestSuite에 소속된 활성 행을 승인된 기본 정렬 순서로 조회한다.
     *
     * <p>{@code (test_suite_id, created_at, id) WHERE deleted_at IS NULL} 부분 인덱스가 이 조회
     * 경로를 지원한다. 정렬을 고정해 TestRun 접수 시점의 대상 집합이 실행마다 흔들리지 않게 한다.
     */
    List<TestCaseEntity> findByTestSuiteIdAndDeletedAtIsNullOrderByCreatedAtAscIdAsc(
            Long testSuiteId);

    long countByTestSuiteIdAndDeletedAtIsNull(Long testSuiteId);

    /**
     * 활성 TestCase 하나를 조건부로 논리 삭제한다.
     *
     * <p>동시 요청의 활성 판정과 쓰기를 UPDATE 한 건으로 묶는다. 반환 값은 영향받은 행 수이며, 0이면
     * 존재하지 않거나 이미 삭제된 TestCase다.
     */
    @Modifying
    @Query(value = """
            UPDATE test_case
               SET deleted_at = :deletedAt,
                   updated_at = :updatedAt
             WHERE id = :id
               AND deleted_at IS NULL
            """, nativeQuery = true)
    int softDeleteIfActive(
            @Param("id") Long id,
            @Param("deletedAt") java.time.Instant deletedAt,
            @Param("updatedAt") java.time.Instant updatedAt);

    /**
     * 주어진 식별자 중 이미 저장된 것만 한 번의 query로 가려낸다.
     *
     * <p>{@code saveAll}이 신규와 기존을 구분하는 데 사용한다. 건마다 존재 여부를 확인하면 배치 크기
     * 만큼 query가 늘어나므로 한 번에 묶어 확인한다.
     */
    @Query("select e.id from TestCaseEntity e where e.id in :ids")
    List<Long> findExistingIds(@Param("ids") Collection<Long> ids);
}
