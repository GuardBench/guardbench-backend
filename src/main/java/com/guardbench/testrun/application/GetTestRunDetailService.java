package com.guardbench.testrun.application;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.LoadTestRunDetailPort;
import com.guardbench.testrun.application.port.out.TestRunDetail;

import org.springframework.stereotype.Service;

/**
 * TestRun 상태·진행률·요약을 조회한다. Polling에 사용하며 TestCase별 개별 결과 배열은 포함하지 않는다.
 *
 * <p>{@code QUEUED}, {@code PREPARING}, {@code RUNNING}, {@code FINISHED} 어느 상태라도 TestRun이
 * 존재하면 조회에 성공한다. Quality Gate가 {@code FAIL}이거나 실행 결과가 {@code ERROR}여도 조회 자체의
 * HTTP 응답은 성공이다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1</a>
 */
@Service
public class GetTestRunDetailService {

    private final LoadTestRunDetailPort loadTestRunDetailPort;

    public GetTestRunDetailService(LoadTestRunDetailPort loadTestRunDetailPort) {
        this.loadTestRunDetailPort = loadTestRunDetailPort;
    }

    public TestRunDetail getTestRun(long testRunId) {
        return loadTestRunDetailPort.load(testRunId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FOUND));
    }
}
