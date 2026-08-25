package com.guardbench.testdefinition.domain;

import java.time.Instant;

/**
 * 현재 편집 가능한 TestCase 정의이며 Aggregate Root다.
 *
 * <p>{@link TestSuite}와는 별도 Aggregate Root이고 소속은 불변 {@link TestSuiteId}로 가리킨다. 소속을
 * 다른 TestSuite로 옮기는 동작은 승인된 API 계약이 허용하지 않으므로 제공하지 않는다.
 *
 * <p>이 객체는 <b>현재 정의만</b> 보유한다. 과거 실행 기준은 TestRun이 접수 시점에 만든 불변 Snapshot이
 * 보존하므로, 이 객체의 수정과 논리 삭제는 이미 만들어진 Snapshot과 실행·판정 결과에 전파되지 않는다.
 * 그 격리는 Snapshot이 값을 복제해 보관하는 구조로 보장되며 이 Aggregate가 Snapshot을 알지 않는다.
 *
 * <p>삭제는 물리 삭제가 아닌 논리 삭제다. 삭제된 TestCase는 현재 조회와 이후 TestRun 대상에서 제외되며
 * 재삭제와 삭제 후 수정을 거부한다.
 *
 * <p>{@code category}는 승인된 API 계약과 같이 고정 Enum이 아닌 비어 있지 않은 문자열로 둔다.
 *
 * <p>식별자는 생성 시점에 부여되며 이후 절대 {@code null}이 아니다. Application이
 * {@code TestCaseRepository.nextIdentity()}로 미리 발급받아 전달하므로, TestSuite와 초기 TestCase를
 * 저장 전에 메모리에서 모두 조립할 수 있고 Application이 persistence flush 순서에 의존하지 않는다.
 *
 * <p>근거: {@code docs/domain/core-model.md},
 * {@code docs/decisions/0001-domain-type-ownership-and-aggregate-boundaries.md},
 * {@code docs/api/README.md}
 */
public final class TestCase {

    private final TestCaseId id;
    private final TestSuiteId testSuiteId;
    private final Instant createdAt;

    private String name;
    private String input;
    private ExpectedResult expectedResult;
    private Severity severity;
    private String category;
    private Instant updatedAt;
    private Instant deletedAt;

    private TestCase(
            TestCaseId id,
            TestSuiteId testSuiteId,
            String name,
            String input,
            ExpectedResult expectedResult,
            Severity severity,
            String category,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {
        this.id = id;
        this.testSuiteId = testSuiteId;
        this.name = name;
        this.input = input;
        this.expectedResult = expectedResult;
        this.severity = severity;
        this.category = category;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    /**
     * 미리 발급받은 식별자로 새 TestCase를 만든다.
     *
     * <p>다섯 정의 값은 모두 필수이며 서버가 숨은 기본값으로 보완하지 않는다.
     */
    public static TestCase create(
            TestCaseId id,
            TestSuiteId testSuiteId,
            String name,
            String input,
            ExpectedResult expectedResult,
            Severity severity,
            String category,
            Instant now) {
        Instant createdAt = requireInstant(now, "생성 시각");

        return new TestCase(
                requireId(id),
                requireTestSuiteId(testSuiteId),
                requireNonBlank(name, "이름"),
                requireNonBlank(input, "입력"),
                requireExpectedResult(expectedResult),
                requireSeverity(severity),
                requireNonBlank(category, "category"),
                createdAt,
                createdAt,
                null);
    }

    /**
     * 저장된 상태에서 Aggregate를 복원한다. Persistence Adapter가 사용한다.
     *
     * <p>{@code deletedAt}이 {@code null}이 아니면 논리 삭제된 TestCase로 복원한다.
     */
    public static TestCase restore(
            TestCaseId id,
            TestSuiteId testSuiteId,
            String name,
            String input,
            ExpectedResult expectedResult,
            Severity severity,
            String category,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt) {
        Instant restoredCreatedAt = requireInstant(createdAt, "생성 시각");
        Instant restoredUpdatedAt = requireInstant(updatedAt, "수정 시각");
        requireNotBefore(restoredUpdatedAt, restoredCreatedAt, "수정 시각");
        if (deletedAt != null) {
            requireNotBefore(deletedAt, restoredCreatedAt, "삭제 시각");
        }

        return new TestCase(
                requireId(id),
                requireTestSuiteId(testSuiteId),
                requireNonBlank(name, "이름"),
                requireNonBlank(input, "입력"),
                requireExpectedResult(expectedResult),
                requireSeverity(severity),
                requireNonBlank(category, "category"),
                restoredCreatedAt,
                restoredUpdatedAt,
                deletedAt);
    }

    /**
     * 현재 정의를 수정한다. {@code null}인 인자는 기존 값 유지를 뜻한다.
     *
     * <p>전달된 값은 먼저 모두 검증한 뒤 반영해 일부만 적용된 상태를 만들지 않는다. 논리 삭제된
     * TestCase는 수정할 수 없다.
     *
     * <p>반영할 값이 현재 값과 모두 같으면 아무 상태도 바꾸지 않고 {@code updatedAt}도 유지한다.
     * 승인된 API 계약이 no-op 수정에 현재 상태를 그대로 반환하도록 요구하고 영속성 계약이
     * {@code updated_at}을 실제 변경 시각으로만 정의하기 때문이다. 이 비교를 Aggregate가 소유하므로
     * 호출자가 상태를 대신 비교하지 않는다.
     *
     * <p>값이 하나도 전달되지 않은 요청은 no-op이 아니라 잘못된 요청이므로
     * {@link IllegalArgumentException}으로 거부한다.
     *
     * <p>근거: {@code docs/api/openapi.yaml},
     * {@code docs/decisions/0002-postgresql-persistence-contract.md}
     */
    public void changeDefinition(
            String name,
            String input,
            ExpectedResult expectedResult,
            Severity severity,
            String category,
            Instant now) {
        requireNotDeleted("수정");

        if (name == null && input == null && expectedResult == null
                && severity == null && category == null) {
            throw new IllegalArgumentException("수정할 값이 최소 하나 필요합니다.");
        }

        String changedName = name == null ? this.name : requireNonBlank(name, "이름");
        String changedInput = input == null ? this.input : requireNonBlank(input, "입력");
        String changedCategory =
                category == null ? this.category : requireNonBlank(category, "category");
        ExpectedResult changedExpectedResult =
                expectedResult == null ? this.expectedResult : expectedResult;
        Severity changedSeverity = severity == null ? this.severity : severity;
        Instant changedAt = requireUpdateInstant(now);

        if (changedName.equals(this.name)
                && changedInput.equals(this.input)
                && changedExpectedResult.equals(this.expectedResult)
                && changedSeverity == this.severity
                && changedCategory.equals(this.category)) {
            return;
        }

        this.name = changedName;
        this.input = changedInput;
        this.expectedResult = changedExpectedResult;
        this.severity = changedSeverity;
        this.category = changedCategory;
        this.updatedAt = changedAt;
    }

    /**
     * TestCase를 논리 삭제한다.
     *
     * <p>삭제 시각과 수정 시각을 같은 값으로 기록한다. 승인된 영속성 계약이 논리 삭제를 상태 변경으로
     * 취급해 {@code deleted_at}과 {@code updated_at}을 같은 시각으로 요구하며, 삭제 후에는 수정을
     * 거부하므로 이 method가 두 시각을 함께 확정한다.
     *
     * <p>이미 삭제된 TestCase를 다시 삭제하면 {@link IllegalStateException}을 던진다. 삭제는 이미
     * 만들어진 Snapshot과 실행·판정 결과에 전파되지 않는다.
     *
     * <p>근거: {@code docs/decisions/0002-postgresql-persistence-contract.md}
     */
    public void delete(Instant now) {
        requireNotDeleted("삭제");

        Instant deletedInstant = requireInstant(now, "삭제 시각");
        requireNotBefore(deletedInstant, createdAt, "삭제 시각");

        this.deletedAt = deletedInstant;
        this.updatedAt = deletedInstant;
    }

    /**
     * 현재 조회와 이후 TestRun 대상에 포함되는지 여부다.
     */
    public boolean isActive() {
        return deletedAt == null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public TestCaseId id() {
        return id;
    }

    public TestSuiteId testSuiteId() {
        return testSuiteId;
    }

    public String name() {
        return name;
    }

    public String input() {
        return input;
    }

    public ExpectedResult expectedResult() {
        return expectedResult;
    }

    public Severity severity() {
        return severity;
    }

    public String category() {
        return category;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant deletedAt() {
        return deletedAt;
    }

    private void requireNotDeleted(String operation) {
        if (isDeleted()) {
            throw new IllegalStateException("논리 삭제된 TestCase는 " + operation + "할 수 없습니다.");
        }
    }

    private Instant requireUpdateInstant(Instant now) {
        Instant candidate = requireInstant(now, "수정 시각");
        requireNotBefore(candidate, createdAt, "수정 시각");

        return candidate;
    }

    private static TestCaseId requireId(TestCaseId id) {
        if (id == null) {
            throw new IllegalArgumentException("TestCase의 TestCaseId는 필수입니다.");
        }

        return id;
    }

    private static TestSuiteId requireTestSuiteId(TestSuiteId testSuiteId) {
        if (testSuiteId == null) {
            throw new IllegalArgumentException("TestCase의 TestSuiteId는 필수입니다.");
        }

        return testSuiteId;
    }

    private static ExpectedResult requireExpectedResult(ExpectedResult expectedResult) {
        if (expectedResult == null) {
            throw new IllegalArgumentException("TestCase의 ExpectedResult는 필수입니다.");
        }

        return expectedResult;
    }

    private static Severity requireSeverity(Severity severity) {
        if (severity == null) {
            throw new IllegalArgumentException("TestCase의 severity는 필수입니다.");
        }

        return severity;
    }

    private static String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TestCase " + label + "은 비어 있을 수 없습니다.");
        }

        return value;
    }

    private static Instant requireInstant(Instant instant, String label) {
        if (instant == null) {
            throw new IllegalArgumentException("TestCase " + label + "은 필수입니다.");
        }

        return instant;
    }

    private static void requireNotBefore(Instant candidate, Instant floor, String label) {
        if (candidate.isBefore(floor)) {
            throw new IllegalArgumentException(
                    "TestCase " + label + "은 생성 시각보다 앞설 수 없습니다.");
        }
    }
}
