package com.guardbench.common.support.fixture;

import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 특정 Logger가 실제로 기록한 로그 이벤트를 캡처하는 테스트 fixture다.
 *
 * <p>Issue #169: application log에 실행 장애와 Quality Gate 판정 근거가 구조화되어
 * 남는지 검증하기 위해 사용한다. Logback appender를 대상 Logger에 부착해
 * 실제로 emit된 로그 메시지를 formatted message 문자열로 확인할 수 있게 한다.
 *
 * <p>테스트가 끝나면 {@link #detach()}로 appender를 반드시 제거해야 한다.
 */
public final class LogCapture {

    private final Logger targetLogger;
    private final ListAppender<ILoggingEvent> appender;

    private LogCapture(Logger targetLogger, ListAppender<ILoggingEvent> appender) {
        this.targetLogger = targetLogger;
        this.appender = appender;
    }

    /**
     * 주어진 클래스의 SLF4J Logger에 캡처용 appender를 부착한다.
     *
     * @param loggedClass 대상 Logger를 소유한 클래스
     * @return 부착된 캡처 fixture
     */
    public static LogCapture attach(Class<?> loggedClass) {
        Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(loggedClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return new LogCapture(logger, appender);
    }

    /** 캡처된 모든 로그 이벤트의 formatted message다. */
    public List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    /** 지정한 부분 문자열을 포함하는 formatted message가 하나 이상 있는지 여부다. */
    public boolean hasMessageContaining(String fragment) {
        return messages().stream().anyMatch(message -> message.contains(fragment));
    }

    /** 지정한 부분 문자열을 포함하는 첫 번째 formatted message다. */
    public String firstMessageContaining(String fragment) {
        return messages().stream()
                .filter(message -> message.contains(fragment))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No captured log message contains: " + fragment));
    }

    /** 캡처를 중단하고 대상 Logger에서 appender를 제거한다. */
    public void detach() {
        targetLogger.detachAppender(appender);
        appender.stop();
    }
}
