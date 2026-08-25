package com.guardbench.testdefinition.infrastructure.persistence;

import java.util.Objects;

import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.ExpectedResult;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testdefinition.domain.TestCase;
import com.guardbench.testdefinition.domain.TestCaseId;
import com.guardbench.testdefinition.domain.TestSuiteId;

/**
 * Domain의 {@code TestCase}와 {@link TestCaseEntity}를 변환한다.
 *
 * <p>{@code expected_action}과 {@code severity}는 이 Mapper가 Enum 이름과 저장 code 사이를 잇는 단일
 * 지점이다. 저장 값이 물리 스키마의 {@code CHECK} 목록에 묶여 있으므로, Enum 이름이 바뀌면 저장 값이
 * 조용히 함께 바뀌는 대신 이 지점에서 드러나게 한다.
 *
 * <p>{@code deletedAt}이 {@code null}이 아니면 논리 삭제된 TestCase로 복원한다. 삭제 여부와 무관하게
 * 다섯 정의 값은 그대로 보존한다.
 *
 * <p>근거: {@code docs/decisions/0002-postgresql-persistence-contract.md}
 */
final class TestCaseEntityMapper {

    private TestCaseEntityMapper() {
    }

    static TestCaseEntity toEntity(TestCase testCase) {
        Objects.requireNonNull(testCase, "TestCase must not be null");

        return new TestCaseEntity(
                testCase.id().value(),
                testCase.testSuiteId().value(),
                testCase.name(),
                testCase.input(),
                testCase.expectedResult().action().name(),
                testCase.severity().name(),
                testCase.category(),
                testCase.createdAt(),
                testCase.updatedAt(),
                testCase.deletedAt());
    }

    static TestCase toDomain(TestCaseEntity entity) {
        Objects.requireNonNull(entity, "TestCaseEntity must not be null");

        return TestCase.restore(
                new TestCaseId(entity.id()),
                new TestSuiteId(entity.testSuiteId()),
                entity.name(),
                entity.input(),
                new ExpectedResult(Action.valueOf(entity.expectedAction())),
                Severity.valueOf(entity.severity()),
                entity.category(),
                entity.createdAt(),
                entity.updatedAt(),
                entity.deletedAt());
    }
}
