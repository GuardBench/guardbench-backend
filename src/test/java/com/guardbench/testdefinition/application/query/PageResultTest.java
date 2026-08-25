package com.guardbench.testdefinition.application.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PageResultTest {

    @Nested
    @DisplayName("전체 페이지 수 계산")
    class TotalPages {

        @Test
        @DisplayName("나누어떨어지지 않으면 마지막 부분 페이지까지 센다")
        void countsTrailingPartialPage() {
            assertEquals(13, page(253, 20).totalPages());
        }

        @Test
        @DisplayName("나누어떨어지면 나머지 페이지를 더하지 않는다")
        void countsExactPagesOnly() {
            assertEquals(10, page(200, 20).totalPages());
        }

        @Test
        @DisplayName("전체 건수가 0이면 페이지 수도 0이다")
        void countsNoPageWhenEmpty() {
            assertEquals(0, page(0, 20).totalPages());
        }
    }

    @Nested
    @DisplayName("이전·다음 페이지 존재 여부")
    class Navigation {

        @Test
        @DisplayName("첫 페이지에는 이전 페이지가 없다")
        void reportsNoPreviousOnFirstPage() {
            assertFalse(new PageResult<>(List.of(), 1, 20, 253).hasPrevious());
        }

        @Test
        @DisplayName("첫 페이지 뒤에 더 있으면 다음 페이지가 있다")
        void reportsNextWhenMorePagesRemain() {
            assertTrue(new PageResult<>(List.of(), 1, 20, 253).hasNext());
        }

        @Test
        @DisplayName("마지막 페이지에는 다음 페이지가 없다")
        void reportsNoNextOnLastPage() {
            assertFalse(new PageResult<>(List.of(), 13, 20, 253).hasNext());
        }

        @Test
        @DisplayName("마지막 페이지를 초과한 요청은 페이지 번호를 유지하고 다음 페이지가 없다")
        void keepsRequestedNumberBeyondLastPage() {
            PageResult<String> result = new PageResult<>(List.of(), 99, 20, 253);

            assertEquals(99, result.number());
            assertTrue(result.items().isEmpty());
            assertFalse(result.hasNext());
            assertTrue(result.hasPrevious());
        }
    }

    @Nested
    @DisplayName("불변식")
    class Invariants {

        @Test
        @DisplayName("전달한 목록을 복사해 보유하므로 이후 원본 변경에 영향받지 않는다")
        void copiesGivenItems() {
            List<String> source = new ArrayList<>(List.of("첫 번째"));
            PageResult<String> result = new PageResult<>(source, 1, 20, 1);

            source.add("나중에 추가");

            assertEquals(List.of("첫 번째"), result.items());
        }

        @Test
        @DisplayName("요청 조건으로 만들면 페이지 번호와 크기를 그대로 사용한다")
        void takesNumberAndSizeFromCriteria() {
            PageResult<String> result = PageResult.of(List.of(), new PageCriteria(3, 50), 120);

            assertEquals(3, result.number());
            assertEquals(50, result.size());
        }

        @Test
        @DisplayName("전체 건수가 음수면 IllegalArgumentException을 던진다")
        void rejectsNegativeTotalElements() {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new PageResult<>(List.of(), 1, 20, -1));
        }

        @Test
        @DisplayName("목록이 null이면 NullPointerException을 던진다")
        void rejectsNullItems() {
            assertThrows(
                    NullPointerException.class,
                    () -> new PageResult<String>(null, 1, 20, 0));
        }
    }

    private static PageResult<String> page(long totalElements, int size) {
        return new PageResult<>(List.of(), 1, size, totalElements);
    }
}
