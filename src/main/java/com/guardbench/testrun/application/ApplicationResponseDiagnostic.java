package com.guardbench.testrun.application;

/** Application response를 운영 로그에 기록하기 위한 제한 값이다. */
final class ApplicationResponseDiagnostic {

    static final int MAX_LOG_LENGTH = 512;
    private static final String TRUNCATION_SUFFIX = "…[truncated]";

    private ApplicationResponseDiagnostic() {
    }

    static DiagnosticValue of(String response) {
        int responseLength = response.codePointCount(0, response.length());
        boolean truncated = responseLength > MAX_LOG_LENGTH;
        String preview = truncated
                ? response.substring(0, response.offsetByCodePoints(0,
                        MAX_LOG_LENGTH - TRUNCATION_SUFFIX.codePointCount(0, TRUNCATION_SUFFIX.length())))
                        + TRUNCATION_SUFFIX
                : response;
        return new DiagnosticValue(responseLength, truncated, escapeControls(preview));
    }

    private static String escapeControls(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        value.codePoints().forEach(codePoint -> {
            switch (codePoint) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (Character.isISOControl(codePoint)) {
                        escaped.append(String.format("\\u%04x", codePoint));
                    } else {
                        escaped.appendCodePoint(codePoint);
                    }
                }
            }
        });
        return escaped.toString();
    }

    record DiagnosticValue(int responseLength, boolean truncated, String preview) {
    }
}
