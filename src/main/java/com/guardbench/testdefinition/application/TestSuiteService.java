package com.guardbench.testdefinition.application;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.application.query.TestSuiteListCriteria;
import com.guardbench.testdefinition.application.query.TestSuiteListQuery;
import com.guardbench.testdefinition.application.query.TestSuiteSummary;
import com.guardbench.testdefinition.domain.ExpectedResult;
import com.guardbench.testdefinition.domain.TestCase;
import com.guardbench.testdefinition.domain.TestSuite;
import com.guardbench.testdefinition.domain.TestSuiteId;
import com.guardbench.testdefinition.domain.repository.TestCaseRepository;
import com.guardbench.testdefinition.domain.repository.TestSuiteRepository;

@Service
@Transactional(readOnly = true)
public class TestSuiteService {

    private final TestSuiteRepository testSuiteRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteListQuery testSuiteListQuery;
    private final Clock clock;

    public TestSuiteService(
            TestSuiteRepository testSuiteRepository,
            TestCaseRepository testCaseRepository,
            TestSuiteListQuery testSuiteListQuery,
            Clock clock) {
        this.testSuiteRepository = testSuiteRepository;
        this.testCaseRepository = testCaseRepository;
        this.testSuiteListQuery = testSuiteListQuery;
        this.clock = clock;
    }

    @Transactional
    public TestSuiteSummary create(TestSuiteCreateCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        TestSuiteId suiteId = testSuiteRepository.nextIdentity();
        TestSuite suite = TestSuite.create(suiteId, command.name(), command.description(), now);

        List<TestCase> testCases = new ArrayList<>(command.testCases().size());
        for (TestCaseCreateCommand testCaseCommand : command.testCases()) {
            testCases.add(TestCase.create(
                    testCaseRepository.nextIdentity(),
                    suiteId,
                    testCaseCommand.name(),
                    testCaseCommand.input(),
                    new ExpectedResult(testCaseCommand.expectedAction()),
                    testCaseCommand.severity(),
                    testCaseCommand.category(),
                    now));
        }

        TestSuite saved = testSuiteRepository.save(suite);
        if (!testCases.isEmpty()) {
            testCaseRepository.saveAll(testCases);
        }
        return summary(saved, testCases.size());
    }

    public PageResult<TestSuiteSummary> list(TestSuiteListCriteria criteria) {
        return testSuiteListQuery.find(criteria);
    }

    public TestSuiteSummary get(long suiteId) {
        TestSuite suite = findSuite(suiteId);
        return summary(suite, testCaseRepository.countActiveByTestSuiteId(suite.id()));
    }

    @Transactional
    public TestSuiteSummary update(long suiteId, TestSuiteUpdateCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        TestSuite suite = findSuite(suiteId);
        Instant now = clock.instant();
        if (command.namePresent()) {
            suite.rename(command.name(), now);
        }
        if (command.descriptionPresent()) {
            suite.changeDescription(command.description(), now);
        }
        TestSuite saved = testSuiteRepository.save(suite);
        return summary(saved, testCaseRepository.countActiveByTestSuiteId(saved.id()));
    }

    private TestSuite findSuite(long suiteId) {
        return testSuiteRepository.findById(new TestSuiteId(suiteId))
                .orElseThrow(() -> new ApplicationException(
                        ApplicationErrorCode.TEST_SUITE_NOT_FOUND));
    }

    private TestSuiteSummary summary(TestSuite suite, long testCaseCount) {
        return new TestSuiteSummary(
                suite.id().value(),
                suite.name(),
                suite.description(),
                testCaseCount,
                suite.createdAt(),
                suite.updatedAt());
    }
}
