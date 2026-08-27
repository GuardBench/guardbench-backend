package com.guardbench.testrun.application;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.LoadTestRunDetailPort;
import com.guardbench.testrun.application.port.out.LoadTestRunResultListPort;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testrun.application.port.out.TestRunResultItem;
import com.guardbench.testrun.application.port.out.TestRunResultListCriteria;
import com.guardbench.testrun.domain.TestRunStatus;

import org.springframework.stereotype.Service;

/**
 * {@code FINISHED} TestRun의 Snapshot별 개별 실행·Assertion·Change 결과를 조회한다.
 *
 * <p>존재하지 않는 TestRun은 {@code TEST_RUN_NOT_FOUND}로, 종료 전 TestRun은
 * {@code TEST_RUN_NOT_FINISHED}로 거부한다. 오류 판단 우선순위 계약에 따라 존재 여부를 먼저 확인한 뒤
 * 상태를 확인한다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1</a>
 * @see <a href="../../../../../../../../docs/conventions/application-errors.md">애플리케이션 오류 코드</a>
 */
@Service
public class GetTestRunResultListService {

    private final LoadTestRunDetailPort loadTestRunDetailPort;
    private final LoadTestRunResultListPort loadTestRunResultListPort;

    public GetTestRunResultListService(
            LoadTestRunDetailPort loadTestRunDetailPort,
            LoadTestRunResultListPort loadTestRunResultListPort) {
        this.loadTestRunDetailPort = loadTestRunDetailPort;
        this.loadTestRunResultListPort = loadTestRunResultListPort;
    }

    public PageResult<TestRunResultItem> getResults(long testRunId, TestRunResultListCriteria criteria) {
        TestRunDetail testRun = loadTestRunDetailPort.load(testRunId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FOUND));
        if (testRun.status() != TestRunStatus.FINISHED) {
            throw new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FINISHED);
        }
        return loadTestRunResultListPort.load(testRunId, criteria);
    }
}
