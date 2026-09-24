package com.guardbench.testrun.infrastructure.integration.evaluation;

import java.util.List;

import org.springframework.stereotype.Component;

import com.guardbench.evaluation.application.CompareStoredRegressionService;
import com.guardbench.evaluation.application.CompareStoredRegressionService.CaseInput;
import com.guardbench.testrun.application.port.out.CompareStoredRegressionPort;
import com.guardbench.testrun.application.port.out.RegressionCaseInput;
import com.guardbench.testrun.application.port.out.RegressionChangeView;

/**
 * TestRun의 값 계약과 Evaluation Application API 사이를 변환하는 Integration Adapter다.
 */
@Component
class EvaluationRegressionIntegrationAdapter implements CompareStoredRegressionPort {

    private final CompareStoredRegressionService comparisonService;

    EvaluationRegressionIntegrationAdapter(CompareStoredRegressionService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @Override
    public List<RegressionChangeView> compare(List<RegressionCaseInput> cases) {
        return comparisonService.compare(cases.stream()
                        .map(input -> new CaseInput(
                                input.testCaseId(),
                                actionCode(input.expectedAction()),
                                actionCode(input.comparisonVerdict()),
                                actionCode(input.currentVerdict())))
                        .toList())
                .stream()
                .map(change -> new RegressionChangeView(
                        change.testCaseId(),
                        change.comparabilityStatusCode(),
                        change.changeTypeCode()))
                .toList();
    }

    private static String actionCode(com.guardbench.testrun.domain.Action action) {
        return action == null ? null : action.name();
    }
}
