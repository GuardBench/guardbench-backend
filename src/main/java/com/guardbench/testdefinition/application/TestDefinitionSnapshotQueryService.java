package com.guardbench.testdefinition.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testdefinition.domain.TestCase;
import com.guardbench.testdefinition.domain.TestSuiteId;
import com.guardbench.testdefinition.domain.repository.TestCaseRepository;
import com.guardbench.testdefinition.domain.repository.TestSuiteRepository;

/**
 * 다른 Bounded Context가 TestDefinition Domain/Repository를 직접 참조하지 않고
 * Snapshot 생성에 필요한 현재 정의를 읽을 수 있게 하는 Application API다.
 */
@Service
@Transactional(readOnly = true)
public class TestDefinitionSnapshotQueryService {

    private final TestSuiteRepository testSuiteRepository;
    private final TestCaseRepository testCaseRepository;

    public TestDefinitionSnapshotQueryService(
            TestSuiteRepository testSuiteRepository,
            TestCaseRepository testCaseRepository
    ) {
        this.testSuiteRepository = testSuiteRepository;
        this.testCaseRepository = testCaseRepository;
    }

    public boolean existsTestSuite(long testSuiteId) {
        return testSuiteRepository.existsById(new TestSuiteId(testSuiteId));
    }

    public List<TestDefinitionSnapshotSource> loadSnapshotSources(long testSuiteId) {
        return testCaseRepository.findByTestSuiteId(new TestSuiteId(testSuiteId)).stream()
                .map(TestDefinitionSnapshotQueryService::toSource)
                .toList();
    }

    private static TestDefinitionSnapshotSource toSource(TestCase testCase) {
        return new TestDefinitionSnapshotSource(
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
