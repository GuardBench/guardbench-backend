package com.guardbench.testrun.application.port.out;

import java.util.List;

import com.guardbench.testrun.domain.SnapshotExecutionStatus;
import com.guardbench.testrun.domain.TestRunId;

/** Snapshot별 ID와 실행 상태만 조회한다. 실행 결과가 없는 Snapshot도 포함한다. */
public interface LoadExecutionStatusesPort {

    List<SnapshotExecutionStatus> load(TestRunId testRunId);
}
