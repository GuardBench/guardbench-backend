package com.guardbench.testrun.application.port.out;

import java.util.List;

/**
 * 지정된 TestRun에 속하는 모든 TestCaseSnapshot ID를 조회하는 consumer-owned Port다.
 * Resolution Worker가 fan-out 메시지를 생성할 때 사용한다.
 */
public interface LoadSnapshotIdsByTestRunPort {

    /**
     * 해당 TestRun에 속하는 TestCaseSnapshot ID를 모두 반환한다.
     * TestRun이 없거나 Snapshot이 없으면 빈 목록이다.
     */
    List<Long> loadSnapshotIdsByTestRunId(long testRunId);
}
