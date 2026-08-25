package com.guardbench.testdefinition.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * {@link TestSuiteEntity}에 대한 Spring Data 접근 지점이다.
 *
 * <p>Domain의 {@code TestSuiteRepository}가 아니다. Domain Port는
 * {@link TestSuiteRepositoryAdapter}가 이 인터페이스를 사용해 구현하며, Spring Data 타입은 이 패키지
 * 밖으로 나가지 않는다.
 *
 * <p>근거: {@code docs/conventions/package-structure.md},
 * {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
interface TestSuiteJpaRepository extends JpaRepository<TestSuiteEntity, Long> {

    /**
     * 물리 스키마의 {@code test_suite_id_seq}에서 다음 값을 얻는다.
     *
     * <p>JPA 표준에는 시퀀스를 직접 읽는 방법이 없어 native query를 사용한다. 시퀀스가
     * {@code INCREMENT BY 50}이므로 호출마다 값이 50씩 뛴다. 승인된 계약이 식별자 연속성을 비즈니스
     * 의미로 사용하지 않으므로 이 간격은 문제가 되지 않는다.
     */
    @Query(value = "SELECT nextval('test_suite_id_seq')", nativeQuery = true)
    long nextSequenceValue();
}
