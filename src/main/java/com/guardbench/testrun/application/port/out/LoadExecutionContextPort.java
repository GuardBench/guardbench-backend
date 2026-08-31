package com.guardbench.testrun.application.port.out;

import java.util.Optional;

/**
 * Execution Worker가 Provider 호출에 필요한 불변 컨텍스트를 조회하는 consumer-owned Port다.
 *
 * <p>TestCaseSnapshot의 input과 TestRun의 불투명 target reference를 하나의 조회로 제공하여
 * Worker가 별도 Repository를 직접 사용하지 않게 한다.
 */
public interface LoadExecutionContextPort {

    /**
     * 지정된 Snapshot과 target type에 해당하는 실행 컨텍스트를 반환한다.
     * Snapshot 또는 TestRun이 존재하지 않으면 empty다.
     */
    Optional<ExecutionContext> load(long snapshotId);
}
