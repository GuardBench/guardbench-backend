package com.guardbench.testdefinition.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.application.query.TestCaseListCriteria;
import com.guardbench.testdefinition.application.query.TestCaseListQuery;
import com.guardbench.testdefinition.application.query.TestCaseSummary;
import com.guardbench.testdefinition.application.port.out.TestCaseBulkIdempotencyPort;
import com.guardbench.testdefinition.application.port.out.TestCaseBulkIdempotencyRecord;
import com.guardbench.testdefinition.domain.ExpectedResult;
import com.guardbench.testdefinition.domain.TestCase;
import com.guardbench.testdefinition.domain.TestCaseId;
import com.guardbench.testdefinition.domain.TestSuiteId;
import com.guardbench.testdefinition.domain.repository.TestCaseRepository;
import com.guardbench.testdefinition.domain.repository.TestSuiteRepository;

@Service
@Transactional(readOnly = true)
public class TestCaseService {

    private static final Duration BULK_IDEMPOTENCY_TTL = Duration.ofHours(3);

    private final TestSuiteRepository testSuiteRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseListQuery testCaseListQuery;
    private final TestCaseBulkIdempotencyPort bulkIdempotencyPort;
    private final Clock clock;

    public TestCaseService(
            TestSuiteRepository testSuiteRepository,
            TestCaseRepository testCaseRepository,
            TestCaseListQuery testCaseListQuery,
            TestCaseBulkIdempotencyPort bulkIdempotencyPort,
            Clock clock) {
        this.testSuiteRepository = testSuiteRepository;
        this.testCaseRepository = testCaseRepository;
        this.testCaseListQuery = testCaseListQuery;
        this.bulkIdempotencyPort = bulkIdempotencyPort;
        this.clock = clock;
    }

    public PageResult<TestCaseSummary> list(
            long suiteId, TestCaseListCriteria criteria) {
        TestSuiteId id = new TestSuiteId(suiteId);
        requireSuite(id);
        if (!criteria.testSuiteId().equals(id)) {
            throw new IllegalArgumentException("조회 조건의 TestSuite 식별자가 일치하지 않습니다.");
        }
        return testCaseListQuery.find(criteria);
    }

    @Transactional
    public TestCaseDetail create(long suiteId, TestCaseCreateCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        TestSuiteId testSuiteId = new TestSuiteId(suiteId);
        requireSuite(testSuiteId);
        Instant now = clock.instant();
        TestCase testCase = TestCase.create(
                testCaseRepository.nextIdentity(),
                testSuiteId,
                command.name(),
                command.input(),
                new ExpectedResult(command.expectedAction()),
                command.severity(),
                command.category(),
                now);
        return TestCaseDetail.from(testCaseRepository.save(testCase));
    }

    @Transactional
    public TestCaseBulkCreateResult createBulk(long suiteId, TestCaseBulkCreateCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        TestSuiteId testSuiteId = new TestSuiteId(suiteId);
        requireSuite(testSuiteId);
        String fingerprint = TestCaseBulkCreateFingerprint.of(suiteId, command);
        Instant now = clock.instant();

        boolean claimed = bulkIdempotencyPort.tryClaim(
                command.idempotencyKey(), fingerprint, suiteId, now, now.plus(BULK_IDEMPOTENCY_TTL));
        if (!claimed) {
            TestCaseBulkIdempotencyRecord existing = bulkIdempotencyPort
                    .findActiveByKey(command.idempotencyKey())
                    .orElseThrow(() -> new ApplicationException(
                            ApplicationErrorCode.INTERNAL_SERVER_ERROR));
            if (!existing.requestFingerprint().equals(fingerprint)
                    || existing.testSuiteId() != suiteId) {
                throw new ApplicationException(ApplicationErrorCode.IDEMPOTENCY_KEY_CONFLICT);
            }
            return new TestCaseBulkCreateResult(
                    existing.createdTestCaseIds(), existing.totalTestCaseCount());
        }

        List<TestCase> testCases = new ArrayList<>(command.items().size());
        for (TestCaseCreateCommand item : command.items()) {
            testCases.add(TestCase.create(
                    testCaseRepository.nextIdentity(),
                    testSuiteId,
                    item.name(),
                    item.input(),
                    new ExpectedResult(item.expectedAction()),
                    item.severity(),
                    item.category(),
                    now));
        }
        List<Long> createdIds = testCaseRepository.saveAll(testCases).stream()
                .map(testCase -> testCase.id().value())
                .toList();
        long totalTestCaseCount = testCaseRepository.countByTestSuiteId(testSuiteId);
        bulkIdempotencyPort.complete(
                command.idempotencyKey(), fingerprint, suiteId, createdIds, totalTestCaseCount);
        return new TestCaseBulkCreateResult(createdIds, totalTestCaseCount);
    }

    public TestCaseDetail get(long testCaseId) {
        return TestCaseDetail.from(find(testCaseId));
    }

    @Transactional
    public TestCaseDetail update(long testCaseId, TestCaseUpdateCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        TestCase testCase = find(testCaseId);
        testCase.changeDefinition(
                command.namePresent() ? command.name() : null,
                command.inputPresent() ? command.input() : null,
                command.expectedActionPresent()
                        ? new ExpectedResult(command.expectedAction()) : null,
                command.severityPresent() ? command.severity() : null,
                command.categoryPresent() ? command.category() : null,
                clock.instant());
        return TestCaseDetail.from(testCaseRepository.save(testCase));
    }

    @Transactional
    public void delete(long testCaseId) {
        TestCaseId id = new TestCaseId(testCaseId);
        if (testCaseRepository.findById(id).isEmpty()) {
            throw new ApplicationException(ApplicationErrorCode.TEST_CASE_NOT_FOUND);
        }
        testCaseRepository.deleteById(id);
    }

    private void requireSuite(TestSuiteId id) {
        if (!testSuiteRepository.existsById(id)) {
            throw new ApplicationException(ApplicationErrorCode.TEST_SUITE_NOT_FOUND);
        }
    }

    private TestCase find(long testCaseId) {
        return testCaseRepository.findById(new TestCaseId(testCaseId))
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.TEST_CASE_NOT_FOUND));
    }
}
