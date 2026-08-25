package com.guardbench.testdefinition.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testdefinition.domain.TestSuite;
import com.guardbench.testdefinition.domain.TestSuiteId;
import com.guardbench.testdefinition.domain.repository.TestSuiteRepository;
import com.guardbench.testsupport.PostgresTestConfiguration;

import jakarta.persistence.EntityManager;

/**
 * Domain Port 계약을 실제 PostgreSQL에서 검증한다.
 *
 * <p>Testcontainers PostgreSQL과 Flyway로 만든 승인된 스키마를 사용한다. 읽기 전에 flush와 clear를
 * 실행해 1차 캐시가 아니라 실제 DB 왕복 결과를 검증한다.
 *
 * @see <a href="file:../docs/decisions/0002-postgresql-persistence-contract.md">ADR 0002</a>
 */
@SpringBootTest
@Import(PostgresTestConfiguration.class)
@Transactional
class TestSuiteRepositoryAdapterIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-25T11:00:00Z");
    private static final TestSuiteId ABSENT_ID = new TestSuiteId(999_999L);

    @Autowired
    private TestSuiteRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("nextIdentity는 호출마다 앞선 값보다 큰 양수 식별자를 발급한다")
    void issuesIncreasingPositiveIdentifiers() {
        TestSuiteId first = repository.nextIdentity();
        TestSuiteId second = repository.nextIdentity();

        assertTrue(first.value() > 0);
        assertTrue(second.value() > first.value());
    }

    @Test
    @DisplayName("발급받은 식별자로 저장한 TestSuite를 DB에서 다시 읽어도 값이 보존된다")
    void preservesValuesWhenReloadedFromDatabase() {
        TestSuiteId id = repository.nextIdentity();
        repository.save(TestSuite.create(id, "안전성 회귀", "PII 차단 정책", CREATED_AT));

        flushAndClear();
        TestSuite reloaded = repository.findById(id).orElseThrow();

        assertEquals(id, reloaded.id());
        assertEquals("안전성 회귀", reloaded.name());
        assertEquals("PII 차단 정책", reloaded.description());
        assertEquals(CREATED_AT, reloaded.createdAt());
        assertEquals(CREATED_AT, reloaded.updatedAt());
    }

    @Test
    @DisplayName("설명이 없는 TestSuite는 DB에서 다시 읽어도 설명이 없다")
    void keepsAbsentDescriptionWhenReloadedFromDatabase() {
        TestSuiteId id = repository.nextIdentity();
        repository.save(TestSuite.create(id, "안전성 회귀", null, CREATED_AT));

        flushAndClear();
        TestSuite reloaded = repository.findById(id).orElseThrow();

        assertNull(reloaded.description());
    }

    @Test
    @DisplayName("수정한 TestSuite를 저장하면 이름과 수정 시각이 갱신된다")
    void appliesRenameToStoredRow() {
        TestSuiteId id = repository.nextIdentity();
        repository.save(TestSuite.create(id, "안전성 회귀", null, CREATED_AT));
        flushAndClear();

        TestSuite loaded = repository.findById(id).orElseThrow();
        loaded.rename("안전성 회귀 v2", UPDATED_AT);
        repository.save(loaded);

        flushAndClear();
        TestSuite reloaded = repository.findById(id).orElseThrow();

        assertEquals("안전성 회귀 v2", reloaded.name());
        assertEquals(UPDATED_AT, reloaded.updatedAt());
        assertEquals(CREATED_AT, reloaded.createdAt());
    }

    @Test
    @DisplayName("저장하지 않은 식별자로 조회하면 빈 Optional을 반환한다")
    void returnsEmptyOptionalForAbsentIdentifier() {
        assertTrue(repository.findById(ABSENT_ID).isEmpty());
    }

    @Test
    @DisplayName("저장하지 않은 식별자의 존재 여부는 false다")
    void reportsAbsentIdentifierAsNotExisting() {
        assertFalse(repository.existsById(ABSENT_ID));
    }

    @Test
    @DisplayName("저장한 식별자의 존재 여부는 true다")
    void reportsStoredIdentifierAsExisting() {
        TestSuiteId id = repository.nextIdentity();
        repository.save(TestSuite.create(id, "안전성 회귀", null, CREATED_AT));

        flushAndClear();

        assertTrue(repository.existsById(id));
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
