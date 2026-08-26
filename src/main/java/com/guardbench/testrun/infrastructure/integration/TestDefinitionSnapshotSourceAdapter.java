package com.guardbench.testrun.infrastructure.integration;

import java.util.List;

import org.springframework.stereotype.Repository;

import com.guardbench.testdefinition.domain.TestCase;
import com.guardbench.testdefinition.domain.TestSuiteId;
import com.guardbench.testdefinition.domain.repository.TestCaseRepository;
import com.guardbench.testdefinition.domain.repository.TestSuiteRepository;
import com.guardbench.testrun.application.port.out.ExistsTestSuitePort;
import com.guardbench.testrun.application.port.out.LoadTestCaseSnapshotSourcesPort;
import com.guardbench.testrun.application.port.out.TestCaseSnapshotSource;

/**
 * {@code testdefinition} Bounded Context를 조회해 TestRun 접수에 필요한 값을 제공하는 Integration
 * Adapter다.
 *
 * <p>{@code testrun}은 {@code testdefinition}의 Domain 타입을 직접 재사용하지 않는다. 이 Adapter만
 * 두 Context 경계를 넘어 {@code testdefinition.TestCase}를 읽고 {@link TestCaseSnapshotSource}처럼
 * {@code testrun}이 소유한 scalar 값 계약으로 명시적으로 변환한다.
 */
@Repository
class TestDefinitionSnapshotSourceAdapter implements ExistsTestSuitePort, LoadTestCaseSnapshotSourcesPort {

    private final TestSuiteRepository testSuiteRepository;
    private final TestCaseRepository testCaseRepository;

    TestDefinitionSnapshotSourceAdapter(
            TestSuiteRepository testSuiteRepository,
            TestCaseRepository testCaseRepository
    ) {
        this.testSuiteRepository = testSuiteRepository;
        this.testCaseRepository = testCaseRepository;
    }

    @Override
    public boolean existsBySourceTestSuiteId(long sourceTestSuiteId) {
        return testSuiteRepository.existsById(new TestSuiteId(sourceTestSuiteId));
    }

    @Override
    public List<TestCaseSnapshotSource> loadBySourceTestSuiteId(long sourceTestSuiteId) {
        List<TestCase> activeTestCases =
                testCaseRepository.findActiveByTestSuiteId(new TestSuiteId(sourceTestSuiteId));
        return activeTestCases.stream().map(this::toSource).toList();
    }

    private TestCaseSnapshotSource toSource(TestCase testCase) {
        return new TestCaseSnapshotSource(
                testCase.testSuiteId().value(),
                testCase.id().value(),
                testCase.name(),
                testCase.input(),
                testCase.expectedResult().action().name(),
                testCase.severity().name(),
                testCase.category()
        );
    }
}
