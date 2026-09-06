package com.guardbench.testrun.application;

import java.util.Objects;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.LoadTestRunDetailPort;
import com.guardbench.testrun.application.port.out.LoadTestRunResultDetailPort;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testrun.application.port.out.TestRunResultDetail;
import com.guardbench.testrun.domain.TestRunStatus;

import org.springframework.stereotype.Service;

/**
 * {@code FINISHED} TestRun의 저장된 Snapshot별 Application response 상세를 조회한다.
 *
 * <p>TestRun 존재와 상태를 먼저 확인한 뒤, Snapshot이 해당 TestRun에 속하는지 함께 검증한다.
 */
@Service
public class GetTestRunResultDetailService {

    private final LoadTestRunDetailPort loadTestRunDetailPort;
    private final LoadTestRunResultDetailPort loadTestRunResultDetailPort;

    public GetTestRunResultDetailService(
            LoadTestRunDetailPort loadTestRunDetailPort,
            LoadTestRunResultDetailPort loadTestRunResultDetailPort) {
        this.loadTestRunDetailPort = Objects.requireNonNull(loadTestRunDetailPort);
        this.loadTestRunResultDetailPort = Objects.requireNonNull(loadTestRunResultDetailPort);
    }

    public TestRunResultDetail getResult(long testRunId, long snapshotId) {
        TestRunDetail testRun = loadTestRunDetailPort.load(testRunId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FOUND));
        if (testRun.status() != TestRunStatus.FINISHED) {
            throw new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FINISHED);
        }
        return loadTestRunResultDetailPort.load(testRunId, snapshotId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.TEST_RUN_RESULT_NOT_FOUND));
    }

}
