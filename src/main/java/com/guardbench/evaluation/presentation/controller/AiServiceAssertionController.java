package com.guardbench.evaluation.presentation.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.guardbench.common.presentation.dto.ApiResponse;
import com.guardbench.evaluation.application.ExecuteAiServiceAssertionService;
import com.guardbench.evaluation.presentation.dto.AiServiceAssertionExecuteReq;
import com.guardbench.evaluation.presentation.dto.AiServiceAssertionExecuteRes;

/**
 * 단일 고객 AI 서비스 endpoint를 대상으로 Assertion을 실행하는 MVP API다.
 */
@RestController
@RequestMapping("/api/v1/ai-service-assertions")
public class AiServiceAssertionController {

    // TODO: 실제 고객 AI 서비스 endpoint가 확정되면 이 값만 교체한다.
    private static final String CUSTOMER_MODEL_ENDPOINT = "https://<CUSTOMER_MODEL_ENDPOINT>";

    private final ExecuteAiServiceAssertionService executeAiServiceAssertionService;

    public AiServiceAssertionController(ExecuteAiServiceAssertionService executeAiServiceAssertionService) {
        this.executeAiServiceAssertionService = executeAiServiceAssertionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AiServiceAssertionExecuteRes>> execute(
            @Valid @RequestBody AiServiceAssertionExecuteReq request) {
        AiServiceAssertionExecuteRes response = AiServiceAssertionExecuteRes.from(
                executeAiServiceAssertionService.execute(CUSTOMER_MODEL_ENDPOINT, request.toCases())
        );
        return ApiResponse.entity(HttpStatus.OK, "AI 서비스 Assertion 실행이 완료되었습니다.", response);
    }
}
