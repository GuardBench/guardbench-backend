package com.guardbench.testrun.application.port.out;

/**
 * Candidate DRAFT를 실행 가능한 Guardrail version으로 고정하는 소비자 소유 Port다.
 *
 * <p>구현 Adapter는 AWS SDK의 BedrockClient를 사용할 수 있지만, 이 계약에는
 * provider 타입을 노출하지 않는다. 외부 호출은 호출자 트랜잭션 밖에서 수행한다.
 */
public interface GuardrailMaterializationPort {

    GuardrailMaterializedVersion materialize(GuardrailMaterializationRequest request);
}
