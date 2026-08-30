package com.guardbench.testrun.domain;

/**
 * TestRun이 실행 대상을 재식별하기 위해 보유하는 불투명 참조다.
 *
 * <p>Provider 종류, 외부 식별자, revision과 lifecycle은 Target 경계가 소유한다.
 */
public record TargetReference(String value) {

    public TargetReference {
        if (value == null || value.codePoints().allMatch(TargetReference::isContractWhitespace)) {
            throw new IllegalArgumentException("target reference must not be blank");
        }
    }

    private static boolean isContractWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint)
                || Character.isSpaceChar(codePoint)
                || codePoint == 0xFEFF;
    }
}
