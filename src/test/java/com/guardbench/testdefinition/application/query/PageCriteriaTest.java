package com.guardbench.testdefinition.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PageCriteriaTest {

    @Test
    @DisplayName("첫 페이지의 offset은 0이다")
    void firstPageStartsAtZeroOffset() {
        assertEquals(0L, new PageCriteria(1, 20).offset());
    }

    @Test
    @DisplayName("1 기반 페이지 번호를 0 기반 offset으로 바꾼다")
    void convertsOneBasedNumberIntoZeroBasedOffset() {
        assertEquals(240L, new PageCriteria(13, 20).offset());
    }

    @Test
    @DisplayName("offset 계산은 int 범위를 넘겨도 넘치지 않는다")
    void computesOffsetWithoutIntegerOverflow() {
        assertEquals(214_748_364_600L, new PageCriteria(Integer.MAX_VALUE, 100).offset());
    }

    @Test
    @DisplayName("firstPage는 승인된 기본 페이지 크기를 사용한다")
    void firstPageUsesApprovedDefaultSize() {
        PageCriteria page = PageCriteria.firstPage();

        assertEquals(1, page.number());
        assertEquals(PageCriteria.DEFAULT_SIZE, page.size());
    }

    @Test
    @DisplayName("페이지 번호가 1보다 작으면 IllegalArgumentException을 던진다")
    void rejectsPageNumberBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> new PageCriteria(0, 20));
    }

    @Test
    @DisplayName("페이지 크기가 허용 범위보다 작으면 IllegalArgumentException을 던진다")
    void rejectsPageSizeBelowMinimum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PageCriteria(1, PageCriteria.MIN_SIZE - 1));
    }

    @Test
    @DisplayName("페이지 크기가 허용 범위보다 크면 IllegalArgumentException을 던진다")
    void rejectsPageSizeAboveMaximum() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PageCriteria(1, PageCriteria.MAX_SIZE + 1));
    }
}
