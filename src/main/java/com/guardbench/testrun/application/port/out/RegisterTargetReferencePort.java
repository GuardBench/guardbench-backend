package com.guardbench.testrun.application.port.out;

import com.guardbench.testrun.domain.TargetReference;

/** TestRun 생성 트랜잭션에서 Target 경계에 실행 대상을 등록한다. */
public interface RegisterTargetReferencePort {

    void register(TargetReference reference, TargetRegistration registration);
}
