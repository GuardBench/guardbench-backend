package com.guardbench.testrun.application;

import java.util.Objects;

import com.guardbench.testrun.application.port.out.TestCaseSnapshotSource;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.ExpectedResult;
import com.guardbench.testrun.domain.Severity;
import com.guardbench.testrun.domain.SourceTestCaseId;
import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestRunId;

public final class TestCaseSnapshotSourceMapper {

    private TestCaseSnapshotSourceMapper() {
    }

    public static TestCaseSnapshot toSnapshot(
            TestCaseSnapshotSource source,
            TestCaseSnapshotId snapshotId,
            TestRunId testRunId
    ) {
        Objects.requireNonNull(source, "snapshot source must not be null");
        return new TestCaseSnapshot(
                snapshotId,
                testRunId,
                new SourceTestCaseId(source.sourceTestCaseId()),
                source.name(),
                source.input(),
                new ExpectedResult(Action.fromCode(source.expectedActionCode())),
                Severity.fromCode(source.severityCode()),
                source.category()
        );
    }
}
