package com.guardbench.testrun.application.port.out;

import com.guardbench.testrun.domain.TestCaseSnapshotId;

/**
 * TestCaseSnapshot 생성 전에 PostgreSQL sequence에서 식별자를 확보하는 consumer-owned Port다.
 */
public interface NextTestCaseSnapshotIdPort {

    TestCaseSnapshotId nextId();
}
