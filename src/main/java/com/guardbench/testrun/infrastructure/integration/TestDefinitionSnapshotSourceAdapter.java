package com.guardbench.testrun.infrastructure.integration;

import java.util.List;

import org.springframework.stereotype.Component;

import com.guardbench.testdefinition.application.TestDefinitionSnapshotQueryService;
import com.guardbench.testdefinition.application.TestDefinitionSnapshotSource;
import com.guardbench.testrun.application.port.out.ExistsTestSuitePort;
import com.guardbench.testrun.application.port.out.LoadTestCaseSnapshotSourcesPort;
import com.guardbench.testrun.application.port.out.TestCaseSnapshotSource;

/**
 * TestDefinition Application 경계를 TestRun의 consumer-owned Port에 연결하는 Integration Adapter다.
 *
 * <p>ADR 0006에 따라 TestDefinition Domain/Repository를 직접 참조하지 않고,
 * 공급 Context가 공개한 scalar/code projection을 TestRun 소유 계약으로 명시적으로 변환한다.
 */
@Component
class TestDefinitionSnapshotSourceAdapter implements ExistsTestSuitePort, LoadTestCaseSnapshotSourcesPort {

    private final TestDefinitionSnapshotQueryService snapshotQueryService;

    TestDefinitionSnapshotSourceAdapter(TestDefinitionSnapshotQueryService snapshotQueryService) {
        this.snapshotQueryService = snapshotQueryService;
    }

    @Override
    public boolean existsBySourceTestSuiteId(long sourceTestSuiteId) {
        return snapshotQueryService.existsTestSuite(sourceTestSuiteId);
    }

    @Override
    public List<TestCaseSnapshotSource> loadBySourceTestSuiteId(long sourceTestSuiteId) {
        return snapshotQueryService.loadSnapshotSources(sourceTestSuiteId).stream()
                .map(TestDefinitionSnapshotSourceAdapter::toSource)
                .toList();
    }

    private static TestCaseSnapshotSource toSource(TestDefinitionSnapshotSource source) {
        return new TestCaseSnapshotSource(
                source.testSuiteId(),
                source.testCaseId(),
                source.name(),
                source.input(),
                source.expectedActionCode(),
                source.severityCode(),
                source.category()
        );
    }
}
