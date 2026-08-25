package com.guardbench.testdefinition.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code test_suite} 행에 대응하는 Persistence Model이다.
 *
 * <p>Domain의 {@code TestSuite}와 분리된 별도 타입이다. Domain에 JPA annotation을 붙이지 않는 승인된
 * 계약을 지키기 위한 것이며, 두 타입 사이 변환은 {@link TestSuiteEntityMapper}가 명시적으로 수행한다.
 *
 * <p>클래스와 접근자를 package-private으로 두어 이 Persistence Model이
 * {@code testdefinition.infrastructure.persistence} 밖으로 새지 않게 한다.
 *
 * <p>식별자는 Application이 저장 전에 발급한 값을 그대로 사용하므로 생성 전략을 두지 않는다. 시각은
 * 물리 스키마의 {@code TIMESTAMPTZ(6)}과 {@link Instant}로 매핑한다.
 *
 * <p>근거: {@code docs/decisions/0002-postgresql-persistence-contract.md},
 * {@code docs/conventions/package-structure.md}
 */
@Entity
@Table(name = "test_suite")
class TestSuiteEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TestSuiteEntity() {
    }

    TestSuiteEntity(
            Long id,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    Long id() {
        return id;
    }

    String name() {
        return name;
    }

    String description() {
        return description;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }
}
