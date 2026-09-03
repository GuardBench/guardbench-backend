package com.guardbench.testdefinition.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.application.query.TestCaseListCriteria;
import com.guardbench.testdefinition.application.query.TestCaseListQuery;
import com.guardbench.testdefinition.application.query.TestCaseSummary;
import com.guardbench.testdefinition.domain.ExpectedResult;
import com.guardbench.testdefinition.domain.TestCase;
import com.guardbench.testdefinition.domain.TestCaseId;
import com.guardbench.testdefinition.domain.TestSuiteId;
import com.guardbench.testdefinition.domain.repository.TestCaseRepository;
import com.guardbench.testdefinition.domain.repository.TestSuiteRepository;

@Service
@Transactional(readOnly = true)
public class TestCaseService {

    private final TestSuiteRepository testSuiteRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestCaseListQuery testCaseListQuery;
    private final Clock clock;

    public TestCaseService(
            TestSuiteRepository testSuiteRepository,
            TestCaseRepository testCaseRepository,
            TestCaseListQuery testCaseListQuery,
            Clock clock) {
        this.testSuiteRepository = testSuiteRepository;
        this.testCaseRepository = testCaseRepository;
        this.testCaseListQuery = testCaseListQuery;
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
