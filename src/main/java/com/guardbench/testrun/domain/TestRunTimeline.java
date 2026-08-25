package com.guardbench.testrun.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * TestRun 수명주기 시각과 그 순서 불변식을 소유하는 Value Object다.
 *
 * @see <a href="../../../../../../../docs/decisions/0002-postgresql-persistence-contract.md">ADR 0002 ck_test_run_time_order</a>
 * @see <a href="../../../../../../../docs/decisions/0004-testrun-finalization-atomicity.md">ADR 0004 완료 시각 불변식</a>
 */
public record TestRunTimeline(Instant createdAt, Instant startedAt, Instant completedAt, Instant updatedAt) {

    public TestRunTimeline {
        Objects.requireNonNull(createdAt, "created time must not be null");
        Objects.requireNonNull(updatedAt, "updated time must not be null");
        requireNotBefore(updatedAt, createdAt, "updated time", "created time");
        if (startedAt != null) {
            requireNotBefore(startedAt, createdAt, "started time", "created time");
        }
        if (completedAt != null) {
            if (startedAt == null) {
                throw new IllegalArgumentException("completed time requires a started time");
            }
            requireNotBefore(completedAt, startedAt, "completed time", "started time");
        }
    }

    public static TestRunTimeline created(Instant createdAt) {
        return new TestRunTimeline(createdAt, null, null, createdAt);
    }

    public TestRunTimeline start(Instant startedAt) {
        if (this.startedAt != null) {
            throw new IllegalStateException("started time is already fixed");
        }
        return new TestRunTimeline(createdAt, startedAt, null, startedAt);
    }

    public TestRunTimeline touch(Instant updatedAt) {
        return new TestRunTimeline(createdAt, startedAt, completedAt, updatedAt);
    }

    public TestRunTimeline complete(Instant completedAt) {
        if (this.completedAt != null) {
            throw new IllegalStateException("completed time is already fixed");
        }
        return new TestRunTimeline(createdAt, startedAt, completedAt, completedAt);
    }

    private static void requireNotBefore(Instant time, Instant reference, String field, String referenceField) {
        if (time.isBefore(reference)) {
            throw new IllegalArgumentException(field + " must not be before " + referenceField);
        }
    }
}
