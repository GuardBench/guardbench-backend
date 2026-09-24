package com.guardbench.testrun.application.port.out;

/**
 * TestRun 생성 트랜잭션에서 Target 경계에 실행 대상을 등록한다.
 *
 * <p>ADR 0006에 따라 다른 Context의 Adapter에 TestRun Domain 타입을 노출하지 않고
 * scalar reference ID와 값 기반 등록 계약만 전달한다.
 */
public interface RegisterTargetReferencePort {

    void register(String referenceId, TargetRegistration registration);
}
