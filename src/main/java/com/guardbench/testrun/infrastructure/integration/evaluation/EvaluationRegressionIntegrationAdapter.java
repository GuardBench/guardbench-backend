package com.guardbench.testrun.infrastructure.integration.evaluation;

import java.util.List;

import com.guardbench.evaluation.domain.EvaluationAction;
import com.guardbench.evaluation.domain.StoredRegressionCase;
import com.guardbench.evaluation.domain.StoredRegressionChange;
import com.guardbench.evaluation.domain.StoredRegressionComparator;
import com.guardbench.testrun.application.port.out.CompareStoredRegressionPort;
import com.guardbench.testrun.application.port.out.RegressionCaseInput;
import com.guardbench.testrun.application.port.out.RegressionChangeView;

import org.springframework.stereotype.Component;

/** TestRun의 scalar/code 계약과 Evaluation Domain 타입 사이의 명시적 변환 Adapter다. */
@Component
class EvaluationRegressionIntegrationAdapter implements CompareStoredRegressionPort {

    private final StoredRegressionComparator comparator = new StoredRegressionComparator();

    @Override
    public List<RegressionChangeView> compare(List<RegressionCaseInput> cases) {
        return comparator.compare(cases.stream()
                        .map(input -> new StoredRegressionCase(
                                input.testCaseId(),
                                toEvaluationAction(input.expectedAction()),
                                toEvaluationAction(input.comparisonVerdict()),
                                toEvaluationAction(input.currentVerdict())))
                        .toList())
                .changes().stream()
                .map(this::toView)
                .toList();
    }

    private RegressionChangeView toView(StoredRegressionChange change) {
        return new RegressionChangeView(
                change.testCaseId(),
                change.result().comparabilityStatus().name(),
                change.result().changeType() == null ? null : change.result().changeType().name());
    }

    private EvaluationAction toEvaluationAction(com.guardbench.testrun.domain.Action action) {
        return action == null ? null : EvaluationAction.valueOf(action.name());
    }
}
