package com.guardbench.testdefinition.presentation.controller;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guardbench.common.presentation.dto.ApiResponse;
import com.guardbench.testdefinition.application.TestSuiteService;
import com.guardbench.testdefinition.application.query.PageResult;
import com.guardbench.testdefinition.application.query.TestSuiteSummary;
import com.guardbench.testdefinition.presentation.dto.TestSuiteCreateReq;
import com.guardbench.testdefinition.presentation.dto.TestSuiteCreateRes;
import com.guardbench.testdefinition.presentation.dto.TestSuiteDetailRes;
import com.guardbench.testdefinition.presentation.dto.TestSuiteListParams;
import com.guardbench.testdefinition.presentation.dto.TestSuiteListRes;
import com.guardbench.testdefinition.presentation.dto.TestSuiteUpdateReq;
import com.guardbench.testdefinition.presentation.dto.TestSuiteUpdateRes;

@Validated
@RestController
@RequestMapping("/api/v1/test-suites")
public class TestSuiteController {

    private final TestSuiteService testSuiteService;

    public TestSuiteController(TestSuiteService testSuiteService) {
        this.testSuiteService = testSuiteService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TestSuiteCreateRes>> create(
            @Valid @RequestBody TestSuiteCreateReq request) {
        TestSuiteCreateRes response = TestSuiteCreateRes.from(
                testSuiteService.create(request.toCommand()));
        return ResponseEntity.created(URI.create("/api/v1/test-suites/" + response.id()))
                .body(ApiResponse.of(
                        HttpStatus.CREATED,
                        "TestSuite가 생성되었습니다.",
                        response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TestSuiteListRes>> list(
            @Valid @ModelAttribute TestSuiteListParams params) {
        PageResult<TestSuiteSummary> result = testSuiteService.list(params.toCriteria());
        return ApiResponse.entity(
                HttpStatus.OK,
                "TestSuite 목록 조회에 성공했습니다.",
                TestSuiteListRes.from(result));
    }

    @GetMapping("/{suiteId}")
    public ResponseEntity<ApiResponse<TestSuiteDetailRes>> get(
            @PathVariable("suiteId")
            @Min(value = 1, message = "TestSuite 식별자는 1 이상이어야 합니다.") long suiteId) {
        return ApiResponse.entity(
                HttpStatus.OK,
                "TestSuite 상세 조회에 성공했습니다.",
                TestSuiteDetailRes.from(testSuiteService.get(suiteId)));
    }

    @PatchMapping("/{suiteId}")
    public ResponseEntity<ApiResponse<TestSuiteUpdateRes>> update(
            @PathVariable("suiteId")
            @Min(value = 1, message = "TestSuite 식별자는 1 이상이어야 합니다.") long suiteId,
            @Valid @RequestBody TestSuiteUpdateReq request) {
        return ApiResponse.entity(
                HttpStatus.OK,
                "TestSuite가 수정되었습니다.",
                TestSuiteUpdateRes.from(testSuiteService.update(suiteId, request.toCommand())));
    }
}
