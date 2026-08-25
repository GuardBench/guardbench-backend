package com.guardbench.testdefinition.infrastructure.persistence;

import java.util.Objects;

import com.guardbench.testdefinition.domain.TestSuite;
import com.guardbench.testdefinition.domain.TestSuiteId;

/**
 * Domain의 {@code TestSuite}와 {@link TestSuiteEntity}를 변환한다.
 *
 * <p>승인된 계약이 Domain과 Persistence Model을 분리하고 Infrastructure Mapper가 둘 사이를 명시적으로
 * 변환하도록 요구한다. 그래서 이 변환을 자동 매핑에 맡기지 않고 필드마다 직접 옮긴다.
 *
 * <p>복원은 {@code TestSuite.restore}를 사용한다. 저장된 상태를 다시 만드는 것이므로 생성 시각을 새로
 * 부여하는 {@code create}를 쓰지 않는다.
 *
 * <p>근거: {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
final class TestSuiteEntityMapper {

    private TestSuiteEntityMapper() {
    }

    static TestSuiteEntity toEntity(TestSuite testSuite) {
        Objects.requireNonNull(testSuite, "TestSuite must not be null");

        return new TestSuiteEntity(
                testSuite.id().value(),
                testSuite.name(),
                testSuite.description(),
                testSuite.createdAt(),
                testSuite.updatedAt());
    }

    static TestSuite toDomain(TestSuiteEntity entity) {
        Objects.requireNonNull(entity, "TestSuiteEntity must not be null");

        return TestSuite.restore(
                new TestSuiteId(entity.id()),
                entity.name(),
                entity.description(),
                entity.createdAt(),
                entity.updatedAt());
    }
}
