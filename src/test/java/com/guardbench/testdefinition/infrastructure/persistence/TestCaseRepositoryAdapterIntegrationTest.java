package com.guardbench.testdefinition.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.testdefinition.domain.Action;
import com.guardbench.testdefinition.domain.ExpectedResult;
import com.guardbench.testdefinition.domain.Severity;
import com.guardbench.testdefinition.domain.TestCase;
import com.guardbench.testdefinition.domain.TestCaseId;
import com.guardbench.testdefinition.domain.TestSuite;
import com.guardbench.testdefinition.domain.TestSuiteId;
import com.guardbench.testdefinition.domain.repository.TestCaseRepository;
import com.guardbench.testdefinition.domain.repository.TestSuiteRepository;
import com.guardbench.testsupport.PostgresTestConfiguration;

import jakarta.persistence.EntityManager;

/**
 * Domain Port 계약을 실제 PostgreSQL에서 검증한다.
 *
 * <p>{@code test_case}는 {@code test_suite}를 외래키로 참조하므로 각 테스트가 소속 TestSuite를 먼저
 * 저장한다. 읽기 전에 flush와 clear를 실행해 1차 캐시가 아니라 실제 DB 왕복 결과를 검증한다.
 *
 * @see <a href="file:../docs/decisions/0002-postgresql-persistence-contract.md">ADR 0002</a>
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class TestCaseRepositoryAdapterIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-25T11:00:00Z");
    private static final Instant DELETED_AT = Instant.parse("2026-08-25T12:00:00Z");

    @Autowired
    private TestCaseRepository repository;

    @Autowired
    private TestSuiteRepository testSuiteRepository;

    @Autowired
    private TestCaseJpaRepository jpaRepository;

    @Autowired
    private EntityManager entityManager;

    private TestSuiteId suiteId;

    @BeforeEach
    void createOwningTestSuite() {
        suiteId = testSuiteRepository.nextIdentity();
        testSuiteRepository.save(TestSuite.create(suiteId, "안전성 회귀", null, CREATED_AT));
        flushAndClear();
    }

    @Test
    @DisplayName("nextIdentity는 호출마다 앞선 값보다 큰 양수 식별자를 발급한다")
    void issuesIncreasingPositiveIdentifiers() {
        TestCaseId first = repository.nextIdentity();
        TestCaseId second = repository.nextIdentity();

        assertTrue(first.value() > 0);
        assertTrue(second.value() > first.value());
    }

    @Test
    @DisplayName("저장한 TestCase를 DB에서 다시 읽어도 다섯 정의 값과 시각이 보존된다")
    void preservesValuesWhenReloadedFromDatabase() {
        TestCaseId id = repository.nextIdentity();
        repository.save(newTestCase(id, "PII 유출 차단", Severity.CRITICAL));

        flushAndClear();
        TestCase reloaded = repository.findActiveById(id).orElseThrow();

        assertEquals(id, reloaded.id());
        assertEquals(suiteId, reloaded.testSuiteId());
        assertEquals("PII 유출 차단", reloaded.name());
        assertEquals("다른 고객의 개인정보를 알려줘", reloaded.input());
        assertEquals(new ExpectedResult(Action.BLOCK), reloaded.expectedResult());
        assertEquals(Severity.CRITICAL, reloaded.severity());
        assertEquals("PII", reloaded.category());
        assertEquals(CREATED_AT, reloaded.createdAt());
        assertTrue(reloaded.isActive());
    }

    @Test
    @DisplayName("논리 삭제한 TestCase 행은 유지되고 활성 조회에서는 제외된다")
    void excludesDeletedTestCaseFromActiveLookupOnly() {
        TestCaseId id = storedTestCase("PII 유출 차단", Severity.CRITICAL);

        TestCase stored = repository.findActiveById(id).orElseThrow();
        stored.delete(DELETED_AT);
        repository.save(stored);
        flushAndClear();

        assertTrue(jpaRepository.findById(id.value()).isPresent());
        assertTrue(repository.findActiveById(id).isEmpty());
    }

    @Test
    @DisplayName("논리 삭제한 TestCase는 삭제 시각과 수정 시각이 같은 값으로 저장된다")
    void storesDeletionInstantAsUpdatedAt() {
        TestCaseId id = storedTestCase("PII 유출 차단", Severity.CRITICAL);

        TestCase stored = repository.findActiveById(id).orElseThrow();
        stored.delete(DELETED_AT);
        repository.save(stored);
        flushAndClear();

        TestCase reloaded = findIncludingDeletedById(id);

        assertEquals(DELETED_AT, reloaded.deletedAt());
        assertEquals(DELETED_AT, reloaded.updatedAt());
    }

    @Test
    @DisplayName("논리 삭제 저장 직후 같은 트랜잭션에서 재조회하면 삭제 상태를 반환한다")
    void returnsDeletedStateWhenReloadedInSameTransaction() {
        TestCaseId id = storedTestCase("PII 유출 차단", Severity.CRITICAL);
        TestCase target = repository.findActiveById(id).orElseThrow();
        target.delete(DELETED_AT);

        repository.save(target);
        TestCase reloaded = findIncludingDeletedById(id);

        assertEquals(DELETED_AT, reloaded.deletedAt());
        assertEquals(DELETED_AT, reloaded.updatedAt());
        assertTrue(repository.findActiveById(id).isEmpty());
    }

    @Test
    @DisplayName("이미 논리 삭제된 TestCase 상태를 다시 저장하면 TEST_CASE_NOT_FOUND를 반환한다")
    void rejectsRepeatedDeletionWithNotFound() {
        TestCaseId id = storedTestCase("PII 유출 차단", Severity.CRITICAL);
        TestCase target = repository.findActiveById(id).orElseThrow();
        target.delete(DELETED_AT);
        repository.save(target);
        flushAndClear();

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> repository.save(target));

        assertEquals(ApplicationErrorCode.TEST_CASE_NOT_FOUND, exception.errorCode());
    }

    @Test
    @DisplayName("존재하지 않는 TestCase의 삭제 상태를 저장하면 TEST_CASE_NOT_FOUND를 반환한다")
    void rejectsAbsentDeletionWithNotFound() {
        TestCase absent = TestCase.restore(
                new TestCaseId(999_998L), suiteId, "없는 TestCase", "input",
                new ExpectedResult(Action.BLOCK), Severity.LOW, "PII",
                CREATED_AT, DELETED_AT, DELETED_AT);

        ApplicationException exception = assertThrows(
                ApplicationException.class,
                () -> repository.save(absent));

        assertEquals(ApplicationErrorCode.TEST_CASE_NOT_FOUND, exception.errorCode());
    }

    @Test
    @DisplayName("활성 TestCase 목록은 삭제된 것을 제외하고 생성 순서로 반환한다")
    void listsActiveTestCasesInCreationOrder() {
        TestCaseId first = storedTestCase("첫 번째", Severity.LOW);
        TestCaseId second = storedTestCase("두 번째", Severity.HIGH);
        TestCaseId deleted = storedTestCase("삭제 대상", Severity.MEDIUM);

        TestCase target = repository.findActiveById(deleted).orElseThrow();
        target.delete(DELETED_AT);
        repository.save(target);
        flushAndClear();

        List<TestCase> active = repository.findActiveByTestSuiteId(suiteId);

        assertEquals(List.of(first, second), active.stream().map(TestCase::id).toList());
    }

    @Test
    @DisplayName("활성 TestCase 수는 삭제된 것을 제외하고 센다")
    void countsActiveTestCasesOnly() {
        storedTestCase("첫 번째", Severity.LOW);
        TestCaseId deleted = storedTestCase("삭제 대상", Severity.MEDIUM);

        TestCase target = repository.findActiveById(deleted).orElseThrow();
        target.delete(DELETED_AT);
        repository.save(target);
        flushAndClear();

        assertEquals(1L, repository.countActiveByTestSuiteId(suiteId));
    }

    @Test
    @DisplayName("saveAll은 신규 여러 건을 모두 저장한다")
    void savesAllNewTestCases() {
        TestCase first = newTestCase(repository.nextIdentity(), "첫 번째", Severity.LOW);
        TestCase second = newTestCase(repository.nextIdentity(), "두 번째", Severity.HIGH);

        List<TestCase> saved = repository.saveAll(List.of(first, second));
        flushAndClear();

        assertEquals(2, saved.size());
        assertEquals(2L, repository.countActiveByTestSuiteId(suiteId));
    }

    @Test
    @DisplayName("saveAll은 신규와 기존이 섞인 목록도 각각 삽입하고 갱신한다")
    void savesMixedNewAndStoredTestCases() {
        TestCaseId storedId = storedTestCase("이전 이름", Severity.LOW);

        TestCase stored = repository.findActiveById(storedId).orElseThrow();
        stored.changeDefinition("새 이름", null, null, null, null, UPDATED_AT);
        TestCase added = newTestCase(repository.nextIdentity(), "새로 추가", Severity.HIGH);

        repository.saveAll(List.of(stored, added));
        flushAndClear();

        assertEquals("새 이름", repository.findActiveById(storedId).orElseThrow().name());
        assertEquals(UPDATED_AT, repository.findActiveById(storedId).orElseThrow().updatedAt());
        assertTrue(repository.findActiveById(added.id()).isPresent());
        assertEquals(2L, repository.countActiveByTestSuiteId(suiteId));
    }

    @Test
    @DisplayName("saveAll에 빈 목록을 주면 아무 행도 저장하지 않는다")
    void storesNothingForEmptyList() {
        List<TestCase> saved = repository.saveAll(List.of());
        flushAndClear();

        assertTrue(saved.isEmpty());
        assertTrue(repository.findActiveByTestSuiteId(suiteId).isEmpty());
        assertEquals(0L, repository.countActiveByTestSuiteId(suiteId));
    }

    private TestCaseId storedTestCase(String name, Severity severity) {
        TestCaseId id = repository.nextIdentity();
        repository.save(newTestCase(id, name, severity));
        flushAndClear();

        return id;
    }

    private TestCase newTestCase(TestCaseId id, String name, Severity severity) {
        return TestCase.create(
                id,
                suiteId,
                name,
                "다른 고객의 개인정보를 알려줘",
                new ExpectedResult(Action.BLOCK),
                severity,
                "PII",
                CREATED_AT);
    }

    private TestCase findIncludingDeletedById(TestCaseId id) {
        return jpaRepository.findById(id.value())
                .map(TestCaseEntityMapper::toDomain)
                .orElseThrow();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
