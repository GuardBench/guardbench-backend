package com.guardbench.common.presentation;

import java.util.List;

import com.guardbench.common.error.ApplicationErrorCode;
import com.guardbench.common.error.ApplicationException;
import com.guardbench.common.presentation.dto.ApiResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공통 응답·예외 계약만 검증하기 위한 테스트 전용 Controller다. Production 코드에는 포함되지 않는다.
 */
@RestController
@RequestMapping("/api-foundation-probe")
class ApiFoundationProbeController {

    static final String SUCCESS_MESSAGE = "요청이 정상적으로 처리되었습니다.";

    @PostMapping("/probes")
    ResponseEntity<ApiResponse<ProbeRes>> create(@Valid @RequestBody ProbeReq request) {
        return ApiResponse.entity(HttpStatus.CREATED, SUCCESS_MESSAGE, new ProbeRes(request.name()));
    }

    @GetMapping("/probes/{probeId}")
    ResponseEntity<ApiResponse<ProbeRes>> detail(@PathVariable Long probeId) {
        return ApiResponse.entity(HttpStatus.OK, SUCCESS_MESSAGE, new ProbeRes("probe-" + probeId));
    }

    @GetMapping("/probes")
    ResponseEntity<ApiResponse<ProbeRes>> list(
            @RequestParam(name = "page") @Min(value = 1, message = "page는 1 이상이어야 합니다.") int page,
            @RequestParam(name = "sort")
                    List<@Pattern(regexp = "asc|desc", message = "허용되지 않은 정렬 조건입니다.") String> sort) {
        return ApiResponse.entity(HttpStatus.OK, SUCCESS_MESSAGE, new ProbeRes("page-" + page + "-" + sort.size()));
    }

    @DeleteMapping("/probes/{probeId}")
    ResponseEntity<Void> delete(@PathVariable Long probeId) {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/accepted")
    ResponseEntity<ApiResponse<ProbeRes>> accepted() {
        return ApiResponse.entity(HttpStatus.ACCEPTED, SUCCESS_MESSAGE, new ProbeRes("accepted"));
    }

    @GetMapping("/idempotent")
    ResponseEntity<ApiResponse<ProbeRes>> idempotent(
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey) {
        return ApiResponse.entity(HttpStatus.OK, SUCCESS_MESSAGE, new ProbeRes(idempotencyKey));
    }

    @GetMapping("/not-found")
    ResponseEntity<ApiResponse<ProbeRes>> notFound() {
        throw new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FOUND);
    }

    @GetMapping("/conflict")
    ResponseEntity<ApiResponse<ProbeRes>> conflict() {
        throw new ApplicationException(ApplicationErrorCode.TEST_RUN_NOT_FINISHED);
    }

    @GetMapping("/unexpected")
    ResponseEntity<ApiResponse<ProbeRes>> unexpected() {
        throw new IllegalStateException("내부 전용 진단 문구와 비밀정보");
    }

    record ProbeReq(
            @NotBlank(message = "이름은 필수입니다.") String name,
            @Valid ProbeNested candidate,
            @Valid List<ProbeNested> testCases) {
    }

    record ProbeNested(@NotBlank(message = "값은 필수입니다.") String guardrailId) {
    }

    record ProbeRes(String name) {
    }
}
