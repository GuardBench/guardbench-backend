package com.guardbench.testrun.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testrun.application.port.out.ExistsTestSuitePort;
import com.guardbench.testrun.application.port.out.IdempotencyPort;
import com.guardbench.testrun.application.port.out.IdempotencyRecord;
import com.guardbench.testrun.application.port.out.LoadTestCaseSnapshotSourcesPort;
import com.guardbench.testrun.application.port.out.NextTestCaseSnapshotIdPort;
import com.guardbench.testrun.application.port.out.NextTestRunIdPort;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.RegisterTargetReferencePort;
import com.guardbench.testrun.application.port.out.RegisterEvaluatorReferencePort;
import com.guardbench.testrun.application.port.out.EvaluatorRegistration;
import com.guardbench.testrun.application.port.out.TargetRegistration;
import com.guardbench.testrun.application.port.out.TestCaseSnapshotSource;
import com.guardbench.testrun.domain.SourceTestSuiteId;
import com.guardbench.testrun.domain.QualityGatePolicy;
import com.guardbench.testrun.domain.EvaluatorReference;
import com.guardbench.testrun.domain.TargetReference;
import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestRun;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.repository.TestCaseSnapshotRepository;
import com.guardbench.testrun.domain.repository.TestRunRepository;

/**
 * TestRun을 멱등하게 접수하는 Application Service다.
 *
 * <p>ADR 0010에 따라 Idempotency 판정, TestSuite/TestCase 확인, TestRun 접수, Target reference·Snapshot
 * 고정과 {@code TestRunRequested} Outbox 저장을 하나의 트랜잭션에서 조율한다. Target
 * preparation, resolution/execution claim과 fan-out은 이 Service의 책임이 아니다.
 *
 * <p>사용자는 evaluator/classifier 설정을 직접 제출하지 않는다. classifier는 서비스 전역 고정
 * 설정({@code evaluatorRegistration})을 사용하며, TestRun은 실제 사용한 provider/model 식별자를
 * {@link EvaluatorReference}로 사후 재식별할 수 있게 고정한다.
 */
@Service
public class CreateTestRunService {

    private static final Logger log = LoggerFactory.getLogger(CreateTestRunService.class);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(3);

    private final ExistsTestSuitePort existsTestSuitePort;
    private final LoadTestCaseSnapshotSourcesPort loadTestCaseSnapshotSourcesPort;
    private final NextTestRunIdPort nextTestRunIdPort;
    private final NextTestCaseSnapshotIdPort nextTestCaseSnapshotIdPort;
    private final TestRunRepository testRunRepository;
    private final TestCaseSnapshotRepository testCaseSnapshotRepository;
    private final OutboxPort outboxPort;
    private final IdempotencyPort idempotencyPort;
    private final RegisterTargetReferencePort registerTargetReferencePort;
    private final RegisterEvaluatorReferencePort registerEvaluatorReferencePort;
    private final EvaluatorRegistration evaluatorRegistration;
    private final Clock clock;

    public CreateTestRunService(
            ExistsTestSuitePort existsTestSuitePort,
            LoadTestCaseSnapshotSourcesPort loadTestCaseSnapshotSourcesPort,
            NextTestRunIdPort nextTestRunIdPort,
            NextTestCaseSnapshotIdPort nextTestCaseSnapshotIdPort,
            TestRunRepository testRunRepository,
            TestCaseSnapshotRepository testCaseSnapshotRepository,
            OutboxPort outboxPort,
            IdempotencyPort idempotencyPort,
            RegisterTargetReferencePort registerTargetReferencePort,
            RegisterEvaluatorReferencePort registerEvaluatorReferencePort,
            EvaluatorRegistration evaluatorRegistration,
            Clock clock
    ) {
        this.existsTestSuitePort = existsTestSuitePort;
        this.loadTestCaseSnapshotSourcesPort = loadTestCaseSnapshotSourcesPort;
        this.nextTestRunIdPort = nextTestRunIdPort;
        this.nextTestCaseSnapshotIdPort = nextTestCaseSnapshotIdPort;
        this.testRunRepository = testRunRepository;
        this.testCaseSnapshotRepository = testCaseSnapshotRepository;
        this.outboxPort = outboxPort;
        this.idempotencyPort = idempotencyPort;
        this.registerTargetReferencePort = registerTargetReferencePort;
        this.registerEvaluatorReferencePort = registerEvaluatorReferencePort;
        this.evaluatorRegistration = evaluatorRegistration;
        this.clock = clock;
    }

    @Transactional
    public TestRunCreateResult create(TestRunCreateCommand command) {
        String fingerprint = TestRunCreateFingerprint.of(command.toIntent());

        if (command.hasIdempotencyKey()) {
            var existing = idempotencyPort.findActiveByKey(command.idempotencyKey());
            if (existing.isPresent()) {
                return reuseOrConflict(existing.get(), fingerprint, command);
            }
        }

        return createNew(command, fingerprint);
    }

    private TestRunCreateResult reuseOrConflict(IdempotencyRecord existing, String fingerprint, TestRunCreateCommand command) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        TestRun testRun = testRunRepository.findById(new TestRunId(existing.testRunId()))
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.INTERNAL_SERVER_ERROR));
        log.info("동일한 요청으로 기존 TestRun 생성을 재사용했습니다. testRunId={} testCaseCount={} status={}",
                testRun.id().value(), testRun.testCaseCount(), testRun.status());
        return toResult(testRun, command);
    }

    private TestRunCreateResult createNew(TestRunCreateCommand command, String fingerprint) {
        if (!"HTTP_ENDPOINT".equals(command.targetType())) {
            throw new ApplicationException(ApplicationErrorCode.VALIDATION_ERROR);
        }
        if (!existsTestSuitePort.existsBySourceTestSuiteId(command.testSuiteId())) {
            throw new ApplicationException(ApplicationErrorCode.TEST_SUITE_NOT_FOUND);
        }
        List<TestCaseSnapshotSource> sources =
                loadTestCaseSnapshotSourcesPort.loadBySourceTestSuiteId(command.testSuiteId());
        if (sources.isEmpty()) {
            throw new ApplicationException(ApplicationErrorCode.TEST_SUITE_EMPTY);
        }

        Instant now = clock.instant();
        TestRunId testRunId = nextTestRunIdPort.nextId();
        TargetReference targetReference = new TargetReference(UUID.randomUUID().toString());
        EvaluatorReference evaluatorReference = new EvaluatorReference(UUID.randomUUID().toString());

        registerTargetReferencePort.register(
                targetReference,
                new TargetRegistration(
                        command.targetType(),
                        command.targetIdentifier(),
                        command.targetRevision(),
                        command.targetModel()));
        registerEvaluatorReferencePort.register(evaluatorReference, evaluatorRegistration);

        TestRun testRun = TestRun.queue(
                testRunId,
                new SourceTestSuiteId(command.testSuiteId()),
                targetReference,
                evaluatorReference,
                new QualityGatePolicy(
                        command.assertionPassRateThreshold(),
                        command.executionSuccessRateThreshold()),
                sources.size(),
                now
        );
        testRunRepository.save(testRun);

        for (TestCaseSnapshotSource source : sources) {
            TestCaseSnapshotId snapshotId = nextTestCaseSnapshotIdPort.nextId();
            TestCaseSnapshot snapshot =
                    TestCaseSnapshotSourceMapper.toSnapshot(source, snapshotId, testRunId, now);
            testCaseSnapshotRepository.save(snapshot);
        }

        OutboxEventRecord requestedEvent = testRunRequestedEvent(testRunId, now);
        outboxPort.save(requestedEvent);

        if (command.hasIdempotencyKey()) {
            idempotencyPort.save(new IdempotencyRecord(
                    command.idempotencyKey(),
                    fingerprint,
                    testRunId.value(),
                    now,
                    now.plus(IDEMPOTENCY_TTL)
            ));
        }

        log.info("TestRun을 접수했습니다. testRunId={} testCaseCount={} targetType={} targetIdentifier={} "
                        + "targetRevision={} targetModel={} evaluatorReferenceId={} evaluatorProviderCode={} "
                        + "evaluatorModelId={} assertionPassRateThreshold={} executionSuccessRateThreshold={} "
                        + "eventId={} eventType={}",
                testRunId.value(), sources.size(), command.targetType(), command.targetIdentifier(),
                command.targetRevision(), command.targetModel(), evaluatorReference.value(),
                evaluatorRegistration.providerCode(), evaluatorRegistration.modelId(),
                command.assertionPassRateThreshold(), command.executionSuccessRateThreshold(),
                requestedEvent.eventId(), requestedEvent.eventType());
        return toResult(testRun, command);
    }

    private OutboxEventRecord testRunRequestedEvent(TestRunId testRunId, Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","eventType":"TestRunRequested","schemaVersion":2,"testRunId":%d,"occurredAt":"%s"}
                """.formatted(eventId, testRunId.value(), occurredAt).strip();
        String deduplicationKey = "TestRunRequested:" + testRunId.value();
        return OutboxEventRecord.pending(eventId, "TestRunRequested", payload, deduplicationKey, occurredAt);
    }

    private TestRunCreateResult toResult(TestRun testRun, TestRunCreateCommand command) {
        return new TestRunCreateResult(
                testRun.id().value(),
                testRun.sourceTestSuiteId().value(),
                testRun.status().name(),
                testRun.testCaseCount(),
                new com.guardbench.testrun.application.port.out.TargetReferenceView(testRun.targetReference().value(), command.targetType(), command.targetIdentifier(), command.targetRevision(), command.targetModel()),
                testRun.timeline().createdAt()
        );
    }
}
