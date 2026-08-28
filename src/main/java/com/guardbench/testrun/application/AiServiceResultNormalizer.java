package com.guardbench.testrun.application;

import java.util.Objects;

import com.guardbench.testrun.application.port.out.AiServiceExecutionResult;
import com.guardbench.testrun.domain.Action;
import com.guardbench.testrun.domain.ActualResult;

/**
 * 고객 AI 서비스의 provider-independent action을 TestRun 결과로 변환한다.
 */
public final class AiServiceResultNormalizer {

    private AiServiceResultNormalizer() {
    }

    public static ActualResult normalize(AiServiceExecutionResult result) {
        Objects.requireNonNull(result, "result must not be null");
        return new ActualResult(Action.fromCode(result.actionCode()));
    }
}
