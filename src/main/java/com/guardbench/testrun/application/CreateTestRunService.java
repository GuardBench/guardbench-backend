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
import com.guardbench.testrun.application.port.out.TestCaseSnapshotSource;
import com.guardbench.testrun.domain.BaselineTarget;
import com.guardbench.testrun.domain.CandidateSource;
import com.guardbench.testrun.domain.CandidateTarget;
import com.guardbench.testrun.domain.SourceTestSuiteId;
import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestRun;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.repository.TestCaseSnapshotRepository;
import com.guardbench.testrun.domain.repository.TestRunRepository;

/**
 * TestRun을 멱등하게 접수하는 Application Service다.
 *
 * <p>ADR 0005/0008에 따라 Idempotency 판정, TestSuite/활성 TestCase 확인, TestRun 접수, Snapshot
 * 고정과 {@code TestRunRequested} Outbox 저장을 하나의 트랜잭션에서 조율한다. Candidate DRAFT
 * materialization, resolution/execution claim과 fan-out은 이 Service의 책임이 아니다(#18 범위).
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
        this.clock = clock;
    }

    @Transactional
    public TestRunCreateResult create(TestRunCreateCommand command) {
        String fingerprint = TestRunCreateFingerprint.of(command.toIntent());

        if (command.hasIdempotencyKey()) {
            var existing = idempotencyPort.findActiveByKey(command.idempotencyKey());
            if (existing.isPresent()) {
                return reuseOrConflict(existing.get(), fingerprint);
            }
        }

        return createNew(command, fingerprint);
    }

    private TestRunCreateResult reuseOrConflict(IdempotencyRecord existing, String fingerprint) {
        if (!existing.requestFingerprint().equals(fingerprint)) {
            throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_KEY_CONFLICT);
        }
        TestRun testRun = testRunRepository.findById(new TestRunId(existing.testRunId()))
                .orElseThrow(() -> new ApplicationException(ApplicationErrorCode.INTERNAL_SERVER_ERROR));
        log.info("TestRun creation reused existing request. testRunId={} testCaseCount={} status={}",
                testRun.id().value(), testRun.testCaseCount(), testRun.status());
        return toResult(testRun);
    }

    private TestRunCreateResult createNew(TestRunCreateCommand command, String fingerprint) {
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

        TestRun testRun = TestRun.queue(
                testRunId,
                new SourceTestSuiteId(command.testSuiteId()),
                new BaselineTarget(command.baselineGuardrailId(), command.baselineVersion()),
                new CandidateTarget(
                        command.candidateGuardrailId(),
                        CandidateSource.valueOf(command.candidateSource()),
                        null),
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

        log.info("TestRun accepted. testRunId={} testCaseCount={} eventId={} eventType={}",
                testRunId.value(), sources.size(), requestedEvent.eventId(), requestedEvent.eventType());
        return toResult(testRun);
    }

    private OutboxEventRecord testRunRequestedEvent(TestRunId testRunId, Instant occurredAt) {
        UUID eventId = UUID.randomUUID();
        String payload = """
                {"eventId":"%s","eventType":"TestRunRequested","schemaVersion":1,"testRunId":%d,"occurredAt":"%s"}
                """.formatted(eventId, testRunId.value(), occurredAt).strip();
        String deduplicationKey = "TestRunRequested:" + testRunId.value();
        return OutboxEventRecord.pending(eventId, "TestRunRequested", payload, deduplicationKey, occurredAt);
    }

    private TestRunCreateResult toResult(TestRun testRun) {
        return new TestRunCreateResult(
                testRun.id().value(),
                testRun.sourceTestSuiteId().value(),
                testRun.status().name(),
                testRun.testCaseCount(),
                testRun.timeline().createdAt()
        );
    }
}
