package com.guardbench.evaluation.application.port.out;

import com.guardbench.evaluation.domain.EvaluationAction;

/**
 * Evaluation이 고객 AI 서비스의 binary action을 얻기 위해 사용하는 출력 Port다.
 */
public interface AiServiceActionPort {

    EvaluationAction execute(String endpoint, String input);
}
