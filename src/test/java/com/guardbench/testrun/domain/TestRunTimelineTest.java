package com.guardbench.testrun.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TestRunTimelineTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-25T00:00:00Z");

    @Test
    @DisplayName("접수 시각만 가진 timeline은 시작·완료 시각이 없다")
    void createdTimelineHasNeitherStartedNorCompletedTime() {
        TestRunTimeline timeline = TestRunTimeline.created(CREATED_AT);

        assertEquals(CREATED_AT, timeline.createdAt());
        assertEquals(CREATED_AT, timeline.updatedAt());
        assertNull(timeline.startedAt());
        assertNull(timeline.completedAt());
    }

    @Test
    @DisplayName("시작 시각이 접수 시각보다 앞서면 거부한다")
    void rejectsStartedTimeBeforeCreatedTime() {
        TestRunTimeline timeline = TestRunTimeline.created(CREATED_AT);

        assertThrows(IllegalArgumentException.class, () -> timeline.start(CREATED_AT.minusSeconds(1)));
    }

    @Test
    @DisplayName("완료 시각이 시작 시각보다 앞서면 거부한다")
    void rejectsCompletedTimeBeforeStartedTime() {
        TestRunTimeline timeline = TestRunTimeline.created(CREATED_AT).start(CREATED_AT.plusSeconds(10));

        assertThrows(IllegalArgumentException.class, () -> timeline.complete(CREATED_AT.plusSeconds(9)));
    }

    @Test
    @DisplayName("갱신 시각이 접수 시각보다 앞서면 거부한다")
    void rejectsUpdatedTimeBeforeCreatedTime() {
        TestRunTimeline timeline = TestRunTimeline.created(CREATED_AT);

        assertThrows(IllegalArgumentException.class, () -> timeline.touch(CREATED_AT.minusSeconds(1)));
    }

    @Test
    @DisplayName("시작 시각 없이 완료 시각만 가질 수 없다")
    void rejectsCompletedTimeWithoutStartedTime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestRunTimeline(CREATED_AT, null, CREATED_AT.plusSeconds(1), CREATED_AT.plusSeconds(1))
        );
    }

    @Test
    @DisplayName("이미 고정된 시작·완료 시각은 다시 설정할 수 없다")
    void rejectsReassigningFixedStartedAndCompletedTime() {
        TestRunTimeline started = TestRunTimeline.created(CREATED_AT).start(CREATED_AT.plusSeconds(1));
        TestRunTimeline completed = started.complete(CREATED_AT.plusSeconds(2));

        assertThrows(IllegalStateException.class, () -> started.start(CREATED_AT.plusSeconds(3)));
        assertThrows(IllegalStateException.class, () -> completed.complete(CREATED_AT.plusSeconds(4)));
    }

    @Test
    @DisplayName("완료 시각을 고정하면 갱신 시각도 완료 시각으로 맞춘다")
    void completionAlignsUpdatedTimeWithCompletedTime() {
        TestRunTimeline timeline = TestRunTimeline.created(CREATED_AT)
                .start(CREATED_AT.plusSeconds(1))
                .complete(CREATED_AT.plusSeconds(2));

        assertEquals(CREATED_AT.plusSeconds(2), timeline.completedAt());
        assertEquals(CREATED_AT.plusSeconds(2), timeline.updatedAt());
    }
}
