package com.guardbench.testdefinition.infrastructure.persistence;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * {@code test_case} 행에 대응하는 Persistence Model이다.
 *
 * <p>소속 TestSuite를 {@code @ManyToOne} 연관이 아니라 {@code test_suite_id} scalar 값으로 보유한다.
 * {@code TestSuite}와 {@code TestCase}는 별도 Aggregate Root이고 Aggregate 사이를 객체 참조로 잇지
 * 않는 승인된 경계를 Persistence Model에서도 유지하기 위한 것이다. DB의 외래키 제약은 그대로 두고
 * 무결성은 DB가 보장한다.
 *
 * <p>{@code expected_action}과 {@code severity}는 Domain Enum이 아니라 문자열 code로 보유한다. 물리
 * 스키마가 두 컬럼을 {@code VARCHAR}와 {@code CHECK}로 정의하므로, 저장되는 값을 Enum 이름에 암묵적으로
 * 묶지 않고 {@link TestCaseEntityMapper}가 명시적으로 변환한다.
 *
 * <p>논리 삭제는 nullable {@code deleted_at}으로 표현한다. 활성 행은 {@code deleted_at IS NULL}이다.
 *
 * <p>근거: {@code docs/decisions/0001-domain-type-ownership-and-aggregate-boundaries.md},
 * {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
@Entity
@Table(name = "test_case")
class TestCaseEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "test_suite_id", nullable = false, updatable = false)
    private Long testSuiteId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "input", nullable = false)
    private String input;

    @Column(name = "expected_action", nullable = false, length = 16)
    private String expectedAction;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected TestCaseEntity() {
    }

    TestCaseEntity(
            Long id,
            Long testSuiteId,
            String name,
            String input,
            String expectedAction,
            String severity,
            String category,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {
        this.id = id;
        this.testSuiteId = testSuiteId;
        this.name = name;
        this.input = input;
        this.expectedAction = expectedAction;
        this.severity = severity;
        this.category = category;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    Long id() {
        return id;
    }

    Long testSuiteId() {
        return testSuiteId;
    }

    String name() {
        return name;
    }

    String input() {
        return input;
    }

    String expectedAction() {
        return expectedAction;
    }

    String severity() {
        return severity;
    }

    String category() {
        return category;
    }

    Instant createdAt() {
        return createdAt;
    }

    Instant updatedAt() {
        return updatedAt;
    }

    Instant deletedAt() {
        return deletedAt;
    }
}
