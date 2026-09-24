package com.guardbench.evaluation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.evaluation.domain.QualityGateResult;
import com.guardbench.evaluation.domain.QualityGateStatus;
import com.guardbench.evaluation.domain.TestRunEvaluationReference;
import com.guardbench.evaluation.domain.repository.QualityGateResultRepository;

/**
 * 다른 Context가 Evaluation Domain/Repository를 직접 참조하지 않고
 * NOT_EVALUATED Quality Gate 결과를 멱등하게 기록할 수 있게 하는 Application API다.
 */
@Service
public class RecordNotEvaluatedQualityGateService {

    private final QualityGateResultRepository qualityGateResultRepository;
    private final Clock clock;

    public RecordNotEvaluatedQualityGateService(
            QualityGateResultRepository qualityGateResultRepository,
            Clock clock
    ) {
        this.qualityGateResultRepository = Objects.requireNonNull(qualityGateResultRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public void record(long testRunId) {
        TestRunEvaluationReference reference = new TestRunEvaluationReference(testRunId);
        if (qualityGateResultRepository.findById(reference).isPresent()) {
            return;
        }

        Instant now = clock.instant();
        qualityGateResultRepository.save(new QualityGateResult(
                reference,
                QualityGateStatus.NOT_EVALUATED,
                null,
                now
        ));
    }
}
