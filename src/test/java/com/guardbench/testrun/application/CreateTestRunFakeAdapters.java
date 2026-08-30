package com.guardbench.testrun.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import com.guardbench.testrun.application.port.out.ExistsTestSuitePort;
import com.guardbench.testrun.application.port.out.IdempotencyPort;
import com.guardbench.testrun.application.port.out.IdempotencyRecord;
import com.guardbench.testrun.application.port.out.LoadTestCaseSnapshotSourcesPort;
import com.guardbench.testrun.application.port.out.NextTestCaseSnapshotIdPort;
import com.guardbench.testrun.application.port.out.NextTestRunIdPort;
import com.guardbench.testrun.application.port.out.OutboxEventRecord;
import com.guardbench.testrun.application.port.out.OutboxPort;
import com.guardbench.testrun.application.port.out.RegisterTargetReferencePort;
import com.guardbench.testrun.application.port.out.TargetRegistration;
import com.guardbench.testrun.application.port.out.TestCaseSnapshotSource;
import com.guardbench.testrun.domain.TestCaseSnapshot;
import com.guardbench.testrun.domain.TestCaseSnapshotId;
import com.guardbench.testrun.domain.TestRun;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.TargetReference;
import com.guardbench.testrun.domain.repository.TestCaseSnapshotRepository;
import com.guardbench.testrun.domain.repository.TestRunRepository;

/**
 * {@link CreateTestRunService} 단위 테스트를 위한 in-memory fake Port 모음이다. 저장 상태를 직접
 * 관찰할 수 있도록 필요한 Port 인터페이스를 한 클래스가 모두 구현한다.
 */
final class CreateTestRunFakeAdapters
        implements ExistsTestSuitePort, LoadTestCaseSnapshotSourcesPort,
        TestRunRepository, TestCaseSnapshotRepository, OutboxPort, IdempotencyPort,
        RegisterTargetReferencePort {

    static final Instant FIXED_NOW = Instant.parse("2026-08-26T00:00:00Z");
    final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private final Set<Long> existingTestSuiteIds = new HashSet<>();
    private final Map<Long, List<TestCaseSnapshotSource>> sourcesByTestSuiteId = new HashMap<>();
    private final AtomicLong testRunIdSequence = new AtomicLong(1);
    private final AtomicLong snapshotIdSequence = new AtomicLong(1);
    private final Map<Long, TestRun> testRunsById = new HashMap<>();
    private final Map<Long, TestCaseSnapshot> snapshotsById = new HashMap<>();
    private final List<OutboxEventRecord> outboxEvents = new ArrayList<>();
    private final Map<String, IdempotencyRecord> idempotencyRecords = new HashMap<>();
    private final Map<String, TargetRegistration> targetRegistrations = new HashMap<>();

    void givenTestSuite(long testSuiteId, List<TestCaseSnapshotSource> sources) {
        existingTestSuiteIds.add(testSuiteId);
        sourcesByTestSuiteId.put(testSuiteId, sources);
    }

    List<OutboxEventRecord> savedOutboxEvents() {
        return outboxEvents;
    }

    Map<Long, TestCaseSnapshot> savedSnapshots() {
        return snapshotsById;
    }

    @Override
    public boolean existsBySourceTestSuiteId(long sourceTestSuiteId) {
        return existingTestSuiteIds.contains(sourceTestSuiteId);
    }

    @Override
    public List<TestCaseSnapshotSource> loadBySourceTestSuiteId(long sourceTestSuiteId) {
        return sourcesByTestSuiteId.getOrDefault(sourceTestSuiteId, List.of());
    }

    NextTestRunIdPort nextTestRunIdPort() {
        return () -> new TestRunId(testRunIdSequence.getAndIncrement());
    }

    NextTestCaseSnapshotIdPort nextTestCaseSnapshotIdPort() {
        return () -> new TestCaseSnapshotId(snapshotIdSequence.getAndIncrement());
    }

    @Override
    public Optional<TestRun> findById(TestRunId id) {
        return Optional.ofNullable(testRunsById.get(id.value()));
    }

    @Override
    public void save(TestRun testRun) {
        testRunsById.put(testRun.id().value(), testRun);
    }

    @Override
    public Optional<TestCaseSnapshot> findById(TestCaseSnapshotId id) {
        return Optional.ofNullable(snapshotsById.get(id.value()));
    }

    @Override
    public List<TestCaseSnapshot> findAllByTestRunId(TestRunId testRunId) {
        return snapshotsById.values().stream()
                .filter(s -> s.testRunId().equals(testRunId))
                .toList();
    }

    @Override
    public void save(TestCaseSnapshot snapshot) {
        snapshotsById.put(snapshot.id().value(), snapshot);
    }

    @Override
    public void save(OutboxEventRecord event) {
        outboxEvents.add(event);
    }

    @Override
    public List<OutboxEventRecord> findPendingBatch(int batchSize) {
        return outboxEvents.stream().limit(batchSize).toList();
    }

    @Override
    public void markPublished(java.util.Collection<UUID> eventIds) {
        // 접수 흐름 검증에는 사용하지 않는다.
    }

    @Override
    public Optional<IdempotencyRecord> findActiveByKey(String idempotencyKey) {
        IdempotencyRecord record = idempotencyRecords.get(idempotencyKey);
        if (record == null || !record.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(record);
    }

    @Override
    public void save(IdempotencyRecord record) {
        idempotencyRecords.put(record.idempotencyKey(), record);
    }

    @Override
    public void register(TargetReference reference, TargetRegistration registration) {
        targetRegistrations.put(reference.value(), registration);
    }
}
