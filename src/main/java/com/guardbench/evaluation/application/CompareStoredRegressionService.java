package com.guardbench.evaluation.application;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.guardbench.evaluation.domain.EvaluationAction;
import com.guardbench.evaluation.domain.StoredRegressionCase;
import com.guardbench.evaluation.domain.StoredRegressionChange;
import com.guardbench.evaluation.domain.StoredRegressionComparator;

/**
 * 저장된 verdict 비교를 Evaluation Domain 내부에 캡슐화해 다른 Context에는 scalar/code만 공개한다.
 */
@Service
public class CompareStoredRegressionService {

    private final StoredRegressionComparator comparator = new StoredRegressionComparator();

    public List<ChangeView> compare(List<CaseInput> cases) {
        Objects.requireNonNull(cases, "cases must not be null");
        return comparator.compare(cases.stream()
                        .map(CompareStoredRegressionService::toDomainCase)
                        .toList())
                .changes().stream()
                .map(CompareStoredRegressionService::toView)
                .toList();
    }

    private static StoredRegressionCase toDomainCase(CaseInput input) {
        return new StoredRegressionCase(
                input.testCaseId(),
                toAction(input.expectedActionCode()),
                toAction(input.comparisonVerdictCode()),
                toAction(input.currentVerdictCode()));
    }

    private static ChangeView toView(StoredRegressionChange change) {
        return new ChangeView(
                change.testCaseId(),
                change.result().comparabilityStatus().name(),
                change.result().changeType() == null ? null : change.result().changeType().name());
    }

    private static EvaluationAction toAction(String code) {
        if (code == null) {
            return null;
        }
        return EvaluationAction.valueOf(code);
    }

    public record CaseInput(
            long testCaseId,
            String expectedActionCode,
            String comparisonVerdictCode,
            String currentVerdictCode
    ) {
    }

    public record ChangeView(long testCaseId, String comparabilityStatusCode, String changeTypeCode) {
    }
}
