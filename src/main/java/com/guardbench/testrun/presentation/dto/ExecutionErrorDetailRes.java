package com.guardbench.testrun.presentation.dto;

/**
 * 사용자에게 안전하게 노출할 수 있는 실행 오류 Detail이다. Provider 원문 응답, stack trace, 내부 예외
 * 메시지는 포함하지 않는다.
 *
 * @see <a href="../../../../../../../../docs/api/openapi.yaml">GuardBench API V1 - ExecutionErrorDetailRes</a>
 */
public record ExecutionErrorDetailRes(String code, String message) {
}
