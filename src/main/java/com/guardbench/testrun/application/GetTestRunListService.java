package com.guardbench.testrun.application;

import com.guardbench.testrun.application.port.out.LoadTestRunListPort;
import com.guardbench.testrun.application.port.out.PageResult;
import com.guardbench.testrun.application.port.out.TestRunListCriteria;
import com.guardbench.testrun.application.port.out.TestRunListItem;

import org.springframework.stereotype.Service;

/**
 * TestRun 목록을 조회한다.
 *
 * <p>승인된 계약상 목록 조회는 존재하지 않는 리소스를 이유로 실패하지 않는다. {@code testSuiteId} filter가
 * 유효한 양의 ID지만 해당 TestSuite가 없어도 {@code 404}가 아니라 빈 {@code items}를 반환한다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1</a>
 */
@Service
public class GetTestRunListService {

    private final LoadTestRunListPort loadTestRunListPort;

    public GetTestRunListService(LoadTestRunListPort loadTestRunListPort) {
        this.loadTestRunListPort = loadTestRunListPort;
    }

    public PageResult<TestRunListItem> getTestRuns(TestRunListCriteria criteria) {
        return loadTestRunListPort.load(criteria);
    }
}
