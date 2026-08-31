package com.guardbench.testrun.application.port.out;

/** Provider SDK 타입을 노출하지 않는 실제 Evaluator 설정 값 계약이다. */
public record EvaluatorRegistration(String typeCode, String identifier, String revision) {
    public EvaluatorRegistration {
        if (typeCode == null || typeCode.isBlank() || identifier == null || identifier.isBlank()
                || revision == null || !revision.matches("[1-9][0-9]{0,7}")) {
            throw new IllegalArgumentException("evaluator registration must contain a numbered revision");
        }
    }
}
