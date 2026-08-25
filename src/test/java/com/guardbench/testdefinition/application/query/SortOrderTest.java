package com.guardbench.testdefinition.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.guardbench.testdefinition.domain.Severity;

class SortOrderTest {

    @Test
    @DisplayName("asc는 오름차순 방향을 가진 조건을 만든다")
    void createsAscendingOrder() {
        SortOrder<Severity> order = SortOrder.asc(Severity.LOW);

        assertEquals(Severity.LOW, order.field());
        assertEquals(SortDirection.ASC, order.direction());
    }

    @Test
    @DisplayName("desc는 내림차순 방향을 가진 조건을 만든다")
    void createsDescendingOrder() {
        assertEquals(SortDirection.DESC, SortOrder.desc(Severity.LOW).direction());
    }

    @Test
    @DisplayName("정렬 필드가 null이면 NullPointerException을 던진다")
    void rejectsNullField() {
        assertThrows(
                NullPointerException.class,
                () -> new SortOrder<Severity>(null, SortDirection.ASC));
    }

    @Test
    @DisplayName("정렬 방향이 null이면 NullPointerException을 던진다")
    void rejectsNullDirection() {
        assertThrows(
                NullPointerException.class,
                () -> new SortOrder<>(Severity.LOW, null));
    }
}
