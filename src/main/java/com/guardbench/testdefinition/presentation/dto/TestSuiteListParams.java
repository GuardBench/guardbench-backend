package com.guardbench.testdefinition.presentation.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import com.guardbench.testdefinition.application.query.PageCriteria;
import com.guardbench.testdefinition.application.query.SortDirection;
import com.guardbench.testdefinition.application.query.SortOrder;
import com.guardbench.testdefinition.application.query.TestSuiteListCriteria;
import com.guardbench.testdefinition.application.query.TestSuiteSortField;
import com.guardbench.testdefinition.presentation.validation.ContractNotBlank;

public final class TestSuiteListParams {

    private static final String SORT_PATTERN =
            "^(name|createdAt|updatedAt|testCaseCount|id),(asc|desc)$";

    @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
    private int page = 1;

    @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
    @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
    private int size = 20;

    @ContractNotBlank(message = "이름 검색어는 비어 있을 수 없습니다.")
    private String name;

    private Instant createdFrom;
    private Instant createdTo;

    @PositiveOrZero(message = "최소 TestCase 개수는 음수일 수 없습니다.")
    private Long minTestCaseCount;

    @PositiveOrZero(message = "최대 TestCase 개수는 음수일 수 없습니다.")
    private Long maxTestCaseCount;

    private List<@Pattern(regexp = SORT_PATTERN, message = "허용되지 않은 정렬 조건입니다.") String>
            sort = new ArrayList<>();

    @AssertTrue(message = "createdFrom은 createdTo보다 늦을 수 없습니다.")
    public boolean isCreatedRangeValid() {
        return createdFrom == null || createdTo == null || !createdFrom.isAfter(createdTo);
    }

    @AssertTrue(message = "minTestCaseCount는 maxTestCaseCount보다 클 수 없습니다.")
    public boolean isTestCaseCountRangeValid() {
        return minTestCaseCount == null
                || maxTestCaseCount == null
                || minTestCaseCount <= maxTestCaseCount;
    }

    public TestSuiteListCriteria toCriteria() {
        return new TestSuiteListCriteria(
                name,
                createdFrom,
                createdTo,
                minTestCaseCount,
                maxTestCaseCount,
                sort.stream().map(this::parseSort).toList(),
                new PageCriteria(page, size));
    }

    private SortOrder<TestSuiteSortField> parseSort(String value) {
        String[] parts = value.split(",", -1);
        TestSuiteSortField field = switch (parts[0]) {
            case "name" -> TestSuiteSortField.NAME;
            case "createdAt" -> TestSuiteSortField.CREATED_AT;
            case "updatedAt" -> TestSuiteSortField.UPDATED_AT;
            case "testCaseCount" -> TestSuiteSortField.TEST_CASE_COUNT;
            case "id" -> TestSuiteSortField.ID;
            default -> throw new IllegalArgumentException("허용되지 않은 정렬 필드입니다.");
        };
        SortDirection direction = "asc".equals(parts[1])
                ? SortDirection.ASC
                : SortDirection.DESC;
        return new SortOrder<>(field, direction);
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedFrom() {
        return createdFrom;
    }

    public void setCreatedFrom(Instant createdFrom) {
        this.createdFrom = createdFrom;
    }

    public Instant getCreatedTo() {
        return createdTo;
    }

    public void setCreatedTo(Instant createdTo) {
        this.createdTo = createdTo;
    }

    public Long getMinTestCaseCount() {
        return minTestCaseCount;
    }

    public void setMinTestCaseCount(Long minTestCaseCount) {
        this.minTestCaseCount = minTestCaseCount;
    }

    public Long getMaxTestCaseCount() {
        return maxTestCaseCount;
    }

    public void setMaxTestCaseCount(Long maxTestCaseCount) {
        this.maxTestCaseCount = maxTestCaseCount;
    }

    public List<String> getSort() {
        return sort;
    }

    public void setSort(List<String> sort) {
        this.sort = sort == null ? new ArrayList<>() : sort;
    }
}
