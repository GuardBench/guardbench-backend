package com.guardbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.guardbench.evaluation.domain.AssertionResult;
import com.guardbench.evaluation.domain.AssertionStatus;
import com.guardbench.evaluation.domain.ChangeResult;
import com.guardbench.evaluation.domain.ChangeType;
import com.guardbench.evaluation.domain.ComparabilityStatus;
import com.guardbench.evaluation.domain.QualityGateMetrics;
import com.guardbench.evaluation.domain.QualityGateResult;
import com.guardbench.evaluation.domain.QualityGateStatus;
import com.guardbench.evaluation.domain.SnapshotEvaluation;
import com.guardbench.evaluation.domain.SnapshotEvaluationReference;
import com.guardbench.evaluation.domain.TestRunEvaluationReference;
import com.guardbench.evaluation.domain.repository.QualityGateResultRepository;
import com.guardbench.evaluation.domain.repository.SnapshotEvaluationRepository;
import com.guardbench.testrun.support.fixture.TestRunPersistenceFixture;
import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class EvaluationPersistenceAdapterIntegrationTest {
    private static final long TEST_SUITE_ID = 500L;
    private static final long FIRST_TEST_CASE_ID = 501L;
    private static final long SECOND_TEST_CASE_ID = 502L;
    private static final long FIRST_TEST_RUN_ID = 600L;
    private static final long SECOND_TEST_RUN_ID = 601L;
    private static final long FIRST_SNAPSHOT_ID = 700L;
    private static final long SECOND_SNAPSHOT_ID = 701L;
    private static final Instant CREATED_AT = Instant.parse("2026-08-26T00:00:00Z");

    private TestRunPersistenceFixture fixture;

    @BeforeEach
    void resetDatabase(@Autowired JdbcTemplate jdbcTemplate) {
        fixture = new TestRunPersistenceFixture(jdbcTemplate);
        fixture.clearPersistenceTables();
        fixture.insertTestSuite(TEST_SUITE_ID, CREATED_AT);
        fixture.insertTestCase(FIRST_TEST_CASE_ID, TEST_SUITE_ID, CREATED_AT);
        fixture.insertTestCase(SECOND_TEST_CASE_ID, TEST_SUITE_ID, CREATED_AT);
        fixture.insertQueuedTestRun(FIRST_TEST_RUN_ID, TEST_SUITE_ID, 2, CREATED_AT);
        fixture.insertQueuedTestRun(SECOND_TEST_RUN_ID, TEST_SUITE_ID, 2, CREATED_AT);
        fixture.insertSnapshot(FIRST_SNAPSHOT_ID, FIRST_TEST_RUN_ID, FIRST_TEST_CASE_ID, CREATED_AT);
        fixture.insertSnapshot(SECOND_SNAPSHOT_ID, FIRST_TEST_RUN_ID, SECOND_TEST_CASE_ID, CREATED_AT);
    }

    @AfterEach
    void clearDatabase() {
        fixture.clearPersistenceTables();
    }

    @Test
    @DisplayName("Assertion-only SnapshotEvaluation은 Application Clock createdAt과 함께 저장·복원한다")
    void persistsAssertionOnlySnapshotEvaluation(
            @Autowired SnapshotEvaluationRepository repository) {
        SnapshotEvaluation evaluation = new SnapshotEvaluation(
                new SnapshotEvaluationReference(FIRST_SNAPSHOT_ID),
                new AssertionResult(AssertionStatus.PASS),
                null,
                CREATED_AT);

        repository.save(evaluation);

        SnapshotEvaluation restored = repository.findById(evaluation.reference()).orElseThrow();
        assertEquals(evaluation, restored);
        assertNull(restored.changeResult());
    }

    @Test
    @DisplayName("Change를 포함한 SnapshotEvaluation은 같은 생성 시각의 하나의 Root로 저장·복원한다")
    void persistsSnapshotEvaluationWithChange(
            @Autowired SnapshotEvaluationRepository repository) {
        SnapshotEvaluation evaluation = new SnapshotEvaluation(
                new SnapshotEvaluationReference(SECOND_SNAPSHOT_ID),
                new AssertionResult(AssertionStatus.FAIL),
                new ChangeResult(
                        ComparabilityStatus.COMPARABLE,
                        ChangeType.SECURITY_REGRESSION),
                CREATED_AT.plusSeconds(1));

        repository.save(evaluation);

        assertEquals(evaluation, repository.findById(evaluation.reference()).orElseThrow());
    }

    @Test
    @DisplayName("같은 SnapshotEvaluation 식별자의 다른 결과를 암묵적으로 덮어쓰지 않는다")
    void doesNotOverwriteExistingSnapshotEvaluation(
            @Autowired SnapshotEvaluationRepository repository) {
        SnapshotEvaluation original = new SnapshotEvaluation(
                new SnapshotEvaluationReference(FIRST_SNAPSHOT_ID),
                new AssertionResult(AssertionStatus.PASS),
                null,
                CREATED_AT);
        SnapshotEvaluation replacement = new SnapshotEvaluation(
                original.reference(),
                new AssertionResult(AssertionStatus.FAIL),
                null,
                CREATED_AT.plusSeconds(1));
        repository.save(original);

        assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class, () -> repository.save(replacement));
        assertEquals(original, repository.findById(original.reference()).orElseThrow());
    }

    @Test
    @DisplayName("PASS QualityGate의 모든 metrics와 Application Clock createdAt을 저장·복원한다")
    void persistsEvaluatedQualityGate(
            @Autowired QualityGateResultRepository repository) {
        QualityGateResult result = new QualityGateResult(
                new TestRunEvaluationReference(FIRST_TEST_RUN_ID),
                QualityGateStatus.PASS,
                new QualityGateMetrics(0.95, 0L, 0.0, 0.05, 0.95),
                CREATED_AT.plusSeconds(2));

        repository.save(result);

        assertEquals(result, repository.findById(result.reference()).orElseThrow());
    }

    @Test
    @DisplayName("NOT_EVALUATED QualityGate는 metrics 전체 null shape를 저장·복원하고 덮어쓰지 않는다")
    void persistsNotEvaluatedQualityGateWithoutMetrics(
            @Autowired QualityGateResultRepository repository) {
        QualityGateResult result = new QualityGateResult(
                new TestRunEvaluationReference(SECOND_TEST_RUN_ID),
                QualityGateStatus.NOT_EVALUATED,
                null,
                CREATED_AT.plusSeconds(3));
        QualityGateResult replacement = new QualityGateResult(
                result.reference(),
                QualityGateStatus.FAIL,
                new QualityGateMetrics(0.0, 1L, 1.0, 0.0, 0.0),
                CREATED_AT.plusSeconds(4));

        repository.save(result);

        QualityGateResult restored = repository.findById(result.reference()).orElseThrow();
        assertEquals(result, restored);
        assertNull(restored.metrics());
        assertThrows(org.springframework.dao.InvalidDataAccessApiUsageException.class, () -> repository.save(replacement));
        assertEquals(result, repository.findById(result.reference()).orElseThrow());
    }
}
