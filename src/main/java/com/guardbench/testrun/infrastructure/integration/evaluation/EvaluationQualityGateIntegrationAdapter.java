package com.guardbench.testrun.infrastructure.integration.evaluation;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.guardbench.evaluation.domain.QualityGateResult;
import com.guardbench.evaluation.domain.QualityGateStatus;
import com.guardbench.evaluation.domain.TestRunEvaluationReference;
import com.guardbench.evaluation.domain.repository.QualityGateResultRepository;
import com.guardbench.testrun.application.port.out.SaveNotEvaluatedQualityGatePort;

/**
 * TestRun Context의 {@link SaveNotEvaluatedQualityGatePort}를 Evaluation Context로 연결하는 Integration Adapter다.
 *
 * <p>ADR 0006에 따라 Integration Adapter만 양쪽 경계를 알 수 있다.
 * TestRun Application Core는 evaluation domain 타입을 직접 import하지 않는다.
 *
 * <p>같은 @Transactional 범위에서 호출되어 ADR 0004의 원자적 저장 불변식을 만족한다.
 */
@Component
class EvaluationQualityGateIntegrationAdapter implements SaveNotEvaluatedQualityGatePort {

    private final QualityGateResultRepository qualityGateResultRepository;
    private final Clock clock;

    EvaluationQualityGateIntegrationAdapter(
            QualityGateResultRepository qualityGateResultRepository,
            Clock clock
    ) {
        this.qualityGateResultRepository = Objects.requireNonNull(qualityGateResultRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public void saveNotEvaluated(long testRunId) {
        TestRunEvaluationReference reference = new TestRunEvaluationReference(testRunId);

        // 이미 존재하면 무시 (멱등)
        if (qualityGateResultRepository.findById(reference).isPresent()) {
            return;
        }

        Instant now = clock.instant();
        QualityGateResult notEvaluated = new QualityGateResult(
                reference,
                QualityGateStatus.NOT_EVALUATED,
                null,
                now
        );
        qualityGateResultRepository.save(notEvaluated);
    }
}
