package com.guardbench.testrun.application;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.EvaluatorMetricsView;
import com.guardbench.testrun.application.port.out.LoadTestRunDetailPort;
import com.guardbench.testrun.application.port.out.LoadTestRunEvaluatorMetricsPort;
import com.guardbench.testrun.application.port.out.TestRunDetail;
import com.guardbench.testrun.domain.TestRunStatus;

import org.springframework.stereotype.Service;

/**
 * FINISHED TestRun의 저장된 Evaluator verdict 분류 지표를 조회한다.
 *
 * <p>존재 여부와 종료 상태를 먼저 확인한 뒤 저장 결과 집계를 수행한다. Application Target 또는
 * Evaluator를 다시 호출하지 않는다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1</a>
 */
@Service
public class GetTestRunEvaluatorMetricsService {

    private final LoadTestRunDetailPort loadTestRunDetailPort;
    private final LoadTestRunEvaluatorMetricsPort loadTestRunEvaluatorMetricsPort;

    public GetTestRunEvaluatorMetricsService(
            LoadTestRunDetailPort loadTestRunDetailPort,
            LoadTestRunEvaluatorMetricsPort loadTestRunEvaluatorMetricsPort) {
        this.loadTestRunDetailPort = loadTestRunDetailPort;
        this.loadTestRunEvaluatorMetricsPort = loadTestRunEvaluatorMetricsPort;
    }

    public EvaluatorMetricsView getMetrics(long testRunId) {
        TestRunDetail testRun = loadTestRunDetailPort.load(testRunId)
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FOUND));
        if (testRun.status() != TestRunStatus.FINISHED) {
            throw new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FINISHED);
        }
        return loadTestRunEvaluatorMetricsPort.load(testRunId);
    }
}
