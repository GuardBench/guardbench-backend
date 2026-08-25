package com.guardbench.testrun.application.port.out;

/**
 * ApplyGuardrail 실행에 필요한 provider-independent 요청 값이다.
 *
 * <p>{@code guardrailVersion}은 AWS ApplyGuardrail의 {@code guardrailVersion} Pattern
 * {@code (|([1-9][0-9]{0,7})|(DRAFT))} 중 숫자형 확정 version만 허용한다. Candidate 경로는
 * DRAFT를 materialize한 뒤 실행하므로 DRAFT와 빈 값은 이 Port에서 받지 않는다.
 *
 * <p>이 제약은 GuardBench의 다른 계층보다 좁다. {@code docs/api/openapi.yaml}과
 * {@code ck_test_run_versions}는 {@code ^[0-9]+$}를 허용하므로 {@code "0"}이나 9자리 값이
 * 저장·입력될 수 있고, 그 값은 여기서 {@link IllegalArgumentException}으로 거부된다. 세 계층의
 * 정렬과 거부 값을 {@link GuardrailFailureCode#TARGET_CONFIGURATION_INVALID}로 정규화하는
 * 책임은 Worker orchestration(#18)에 있으며 이 Port의 범위가 아니다.
 *
 * <p>{@code guardrailIdentifier}는 non-blank만 검증한다. AWS Pattern
 * {@code (|([a-z0-9]+)|(arn:aws(-[^:]+)?:bedrock:[a-z0-9-]{1,20}:[0-9]{12}:guardrail/[a-z0-9]+))}과
 * 최대 2048자 제약을 이 Port에서 강제할지는 계약을 좁히는 결정이므로 별도로 판단한다.
 *
 * @see <a href="https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_ApplyGuardrail.html">ApplyGuardrail API</a>
 */
public record GuardrailExecutionRequest(
        String guardrailIdentifier,
        String guardrailVersion,
        String input
) {

    public GuardrailExecutionRequest {
        validateText(guardrailIdentifier, "guardrail identifier");
        if (guardrailVersion == null || !guardrailVersion.matches("[1-9][0-9]{0,7}")) {
            throw new IllegalArgumentException("guardrail version must be a positive numeric version");
        }
        validateText(input, "input");
    }

    private static void validateText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
