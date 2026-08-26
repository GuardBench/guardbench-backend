package com.guardbench.testdefinition.presentation.controller;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guardbench.common.presentation.dto.ApiResponse;
import com.guardbench.testdefinition.application.TestCaseService;
import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.application.query.TestCaseSummary;
import com.guardbench.testdefinition.presentation.dto.TestCaseCreateReq;
import com.guardbench.testdefinition.presentation.dto.TestCaseCreateRes;
import com.guardbench.testdefinition.presentation.dto.TestCaseDetailRes;
import com.guardbench.testdefinition.presentation.dto.TestCaseListParams;
import com.guardbench.testdefinition.presentation.dto.TestCaseListRes;
import com.guardbench.testdefinition.presentation.dto.TestCaseUpdateReq;
import com.guardbench.testdefinition.presentation.dto.TestCaseUpdateRes;

@Validated
@RestController
@RequestMapping("/api/v1")
public class TestCaseController {

    private final TestCaseService testCaseService;

    public TestCaseController(TestCaseService testCaseService) {
        this.testCaseService = testCaseService;
    }

    @GetMapping("/test-suites/{suiteId}/test-cases")
    public ResponseEntity<ApiResponse<TestCaseListRes>> list(
            @PathVariable("suiteId") @Min(value = 1, message = "TestSuite 식별자는 1 이상이어야 합니다.")
            long suiteId,
            @Valid @ModelAttribute TestCaseListParams params) {
        PageResult<TestCaseSummary> result = testCaseService.list(suiteId, params.toCriteria(suiteId));
        return ApiResponse.entity(
                HttpStatus.OK, "TestCase 목록 조회에 성공했습니다.", TestCaseListRes.from(result));
    }

    @PostMapping("/test-suites/{suiteId}/test-cases")
    public ResponseEntity<ApiResponse<TestCaseCreateRes>> create(
            @PathVariable("suiteId") @Min(value = 1, message = "TestSuite 식별자는 1 이상이어야 합니다.")
            long suiteId,
            @Valid @RequestBody TestCaseCreateReq request) {
        TestCaseCreateRes response = TestCaseCreateRes.from(
                testCaseService.create(suiteId, request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/test-cases/" + response.id()))
                .body(ApiResponse.of(HttpStatus.CREATED, "TestCase가 생성되었습니다.", response));
    }

    @GetMapping("/test-cases/{testCaseId}")
    public ResponseEntity<ApiResponse<TestCaseDetailRes>> get(
            @PathVariable("testCaseId") @Min(value = 1, message = "TestCase 식별자는 1 이상이어야 합니다.")
            long testCaseId) {
        return ApiResponse.entity(
                HttpStatus.OK,
                "TestCase 상세 조회에 성공했습니다.",
                TestCaseDetailRes.from(testCaseService.get(testCaseId)));
    }

    @PatchMapping("/test-cases/{testCaseId}")
    public ResponseEntity<ApiResponse<TestCaseUpdateRes>> update(
            @PathVariable("testCaseId") @Min(value = 1, message = "TestCase 식별자는 1 이상이어야 합니다.")
            long testCaseId,
            @Valid @RequestBody TestCaseUpdateReq request) {
        return ApiResponse.entity(
                HttpStatus.OK,
                "TestCase가 수정되었습니다.",
                TestCaseUpdateRes.from(testCaseService.update(testCaseId, request.toCommand())));
    }

    @DeleteMapping("/test-cases/{testCaseId}")
    public ResponseEntity<Void> delete(
            @PathVariable("testCaseId") @Min(value = 1, message = "TestCase 식별자는 1 이상이어야 합니다.")
            long testCaseId) {
        testCaseService.delete(testCaseId);
        return ResponseEntity.noContent().build();
    }
}
