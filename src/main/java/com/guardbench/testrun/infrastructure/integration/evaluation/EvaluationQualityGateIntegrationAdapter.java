package com.guardbench.testrun.infrastructure.integration.evaluation;

import org.springframework.stereotype.Component;

import com.guardbench.evaluation.application.RecordNotEvaluatedQualityGateService;
import com.guardbench.testrun.application.port.out.SaveNotEvaluatedQualityGatePort;

/**
 * TestRun Context의 consumer-owned Port를 Evaluation Application 경계에 연결한다.
 */
@Component
class EvaluationQualityGateIntegrationAdapter implements SaveNotEvaluatedQualityGatePort {

    private final RecordNotEvaluatedQualityGateService qualityGateService;

    EvaluationQualityGateIntegrationAdapter(RecordNotEvaluatedQualityGateService qualityGateService) {
        this.qualityGateService = qualityGateService;
    }

    @Override
    public void saveNotEvaluated(long testRunId) {
        qualityGateService.record(testRunId);
    }
}
