package com.guardbench.testrun.presentation.controller;

import java.net.URI;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guardbench.common.presentation.dto.ApiResponse;
import com.guardbench.testrun.application.CreateTestRunService;
import com.guardbench.testrun.presentation.dto.TestRunCreateReq;
import com.guardbench.testrun.presentation.dto.TestRunCreateRes;

/**
 * TestRun 비동기 실행 요청 접수 API다. 실행 완료가 아니라 요청 접수만 담당하며 Worker 실행과 실제
 * Bedrock 호출은 이 Controller의 책임이 아니다(#18 범위).
 *
 * <p>클래스 레벨 {@code @Validated}(AOP 방식)는 사용하지 않는다. AOP 방식은
 * {@code ConstraintViolationException}을 던지며 공통 {@code GlobalExceptionHandler}가 이를 처리하지
 * 못해 500으로 응답한다. 파라미터 애노테이션만으로 Spring MVC의
 * {@code HandlerMethodValidationException} 경로를 사용해 {@code VALIDATION_ERROR}로 변환한다.
 */
@RestController
@RequestMapping("/api/v1/test-runs")
public class TestRunCommandController {

    private final CreateTestRunService createTestRunService;

    public TestRunCommandController(CreateTestRunService createTestRunService) {
        this.createTestRunService = createTestRunService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TestRunCreateRes>> create(
            @RequestHeader(name = "Idempotency-Key", required = false)
            @Size(min = 1, max = 100, message = "Idempotency-Key는 1자 이상 100자 이하여야 합니다.")
            String idempotencyKey,
            @Valid @RequestBody TestRunCreateReq request) {
        TestRunCreateRes response = TestRunCreateRes.from(
                createTestRunService.create(request.toCommand(idempotencyKey)));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/test-runs/" + response.id()))
                .body(ApiResponse.of(
                        HttpStatus.ACCEPTED,
                        "TestRun 실행 요청이 접수되었습니다.",
                        response));
    }
}
