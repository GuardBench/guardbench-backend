package com.guardbench.testdefinition.domain;

import java.time.Instant;

/**
 * 관련 TestCase를 묶는 정책 테스트 자산이며 Aggregate Root다.
 *
 * <p>{@code TestCase}와는 별도 Aggregate Root이므로 TestCase 컬렉션을 내부에 보유하지 않는다. 소속
 * 관계는 {@code TestCase}가 {@link TestSuiteId}로 가리킨다. 덕분에 TestCase의 독립 조회·수정·논리
 * 삭제와 페이지 조회가 이 Aggregate를 경합 지점으로 만들지 않는다.
 *
 * <p>{@code testCaseCount}처럼 TestCase 집합에서 파생되는 값은 이 Aggregate가 관리하지 않는다.
 * 조회 시점에 계산하거나 조회 전용 Projection이 제공한다.
 *
 * <p>식별자는 생성 시점에 부여되며 이후 절대 {@code null}이 아니다. Application이
 * {@code TestSuiteRepository.nextIdentity()}로 미리 발급받아 전달하므로, TestSuite와 초기 TestCase를
 * 저장 전에 메모리에서 모두 조립할 수 있고 Application이 persistence flush 순서에 의존하지 않는다.
 *
 * <p>시각은 이 객체가 직접 만들지 않고 호출자가 전달한다. Application 계층이 시간 원천을 소유해
 * Domain을 결정적으로 테스트할 수 있게 한다.
 *
 * <p>근거: {@code docs/domain/core-model.md},
 * {@code docs/decisions/0001-domain-type-ownership-and-aggregate-boundaries.md}
 */
public final class TestSuite {

    private final TestSuiteId id;
    private final Instant createdAt;

    private String name;
    private String description;
    private Instant updatedAt;

    private TestSuite(
            TestSuiteId id,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * 미리 발급받은 식별자로 새 TestSuite를 만든다.
     *
     * <p>{@code description}은 선택 값이며 {@code null}을 허용한다. 빈 문자열이나 공백만 있는 값은
     * 값이 없는 것과 같으므로 {@code null}로 정규화한다.
     */
    public static TestSuite create(
            TestSuiteId id,
            String name,
            String description,
            Instant now) {
        Instant createdAt = requireInstant(now, "생성 시각");

        return new TestSuite(
                requireId(id),
                requireNonBlankName(name),
                normalizeDescription(description),
                createdAt,
                createdAt);
    }

    /**
     * 저장된 상태에서 Aggregate를 복원한다. Persistence Adapter가 사용한다.
     */
    public static TestSuite restore(
            TestSuiteId id,
            String name,
            String description,
            Instant createdAt,
            Instant updatedAt) {
        Instant restoredCreatedAt = requireInstant(createdAt, "생성 시각");
        Instant restoredUpdatedAt = requireInstant(updatedAt, "수정 시각");
        requireNotBefore(restoredUpdatedAt, restoredCreatedAt, "수정 시각");

        return new TestSuite(
                requireId(id),
                requireNonBlankName(name),
                normalizeDescription(description),
                restoredCreatedAt,
                restoredUpdatedAt);
    }

    /**
     * 이름과 설명을 수정한다.
     *
     * <p>{@code name}이 {@code null}이면 기존 이름을 유지한다. {@code description}은 값 제거를 허용
     * 해야 하므로 이 method의 인자로 구분할 수 없다. 설명만 비우려면
     * {@link #changeDescription(String, Instant)}를 사용한다.
     */
    public void rename(String name, Instant now) {
        String changedName = requireNonBlankName(name);
        Instant changedAt = requireUpdateInstant(now);

        this.name = changedName;
        this.updatedAt = changedAt;
    }

    /**
     * 설명을 수정한다. {@code null}, 빈 문자열과 공백 문자열은 설명 제거로 처리한다.
     */
    public void changeDescription(String description, Instant now) {
        Instant changedAt = requireUpdateInstant(now);

        this.description = normalizeDescription(description);
        this.updatedAt = changedAt;
    }

    public TestSuiteId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private Instant requireUpdateInstant(Instant now) {
        Instant candidate = requireInstant(now, "수정 시각");
        requireNotBefore(candidate, createdAt, "수정 시각");

        return candidate;
    }

    private static TestSuiteId requireId(TestSuiteId id) {
        if (id == null) {
            throw new IllegalArgumentException("TestSuite의 TestSuiteId는 필수입니다.");
        }

        return id;
    }

    private static String requireNonBlankName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("TestSuite 이름은 비어 있을 수 없습니다.");
        }

        return name;
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }

        return description;
    }

    private static Instant requireInstant(Instant instant, String label) {
        if (instant == null) {
            throw new IllegalArgumentException("TestSuite " + label + "은 필수입니다.");
        }

        return instant;
    }

    private static void requireNotBefore(Instant candidate, Instant floor, String label) {
        if (candidate.isBefore(floor)) {
            throw new IllegalArgumentException(
                    "TestSuite " + label + "은 생성 시각보다 앞설 수 없습니다.");
        }
    }
}
