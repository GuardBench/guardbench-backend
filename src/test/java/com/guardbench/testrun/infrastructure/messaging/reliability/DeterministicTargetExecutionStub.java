package com.guardbench.testrun.infrastructure.messaging.reliability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.guardbench.testrun.application.port.out.TargetExecutionPort;
import com.guardbench.testrun.application.port.out.TargetExecutionRequest;
import com.guardbench.testrun.application.port.out.TargetExecutionResult;
import com.guardbench.testrun.application.port.out.TargetFailureCode;

/**
 * Issue #153: LocalStack reliability integration test용 deterministic Provider Stub이다.
 *
 * <p>실제 Bedrock/HTTP Application Target 호출을 대체하여, SQS delivery retry와
 * Provider business retry를 분리 관측할 수 있게 한다. 이 Stub은 실제 invocation
 * 횟수를 요청의 {@code input} 텍스트별로 계수하므로, 테스트가 TestCaseSnapshot마다
 * 고유한 input을 사용하면 SQS {@code ApproximateReceiveCount}와 이 Stub의
 * invocation count를 독립적으로 비교할 수 있다.
 *
 * <p>단일 Spring 빈으로 등록되어 테스트 메서드 간 재사용되므로, 모드 전환({@link #useAlwaysSucceed()} 등)과
 * {@link #reset()}으로 각 테스트가 독립된 상태에서 시작하도록 한다.
 *
 * <p>지원 모드:
 * <ul>
 *   <li>{@code SUCCESS} — 매 호출마다 성공</li>
 *   <li>{@code PERMANENT_FAILURE} — 매 호출마다 재시도 불가능한 실패({@code TARGET_CONFIGURATION_INVALID})</li>
 *   <li>{@code ALWAYS_TIMEOUT} — 매 호출마다 {@code PROVIDER_TIMEOUT} (재시도 가능 실패)</li>
 *   <li>{@code TIMEOUT_N_TIMES_THEN_SUCCESS} — 처음 N회는 timeout, 이후 성공</li>
 * </ul>
 */
public final class DeterministicTargetExecutionStub implements TargetExecutionPort {

    public enum Mode {
        SUCCESS,
        PERMANENT_FAILURE,
        ALWAYS_TIMEOUT,
        TIMEOUT_N_TIMES_THEN_SUCCESS
    }

    private final Map<String, AtomicInteger> invocationCounts = new ConcurrentHashMap<>();
    private final AtomicReference<Mode> defaultMode = new AtomicReference<>(Mode.SUCCESS);
    private final Map<String, Mode> perKeyMode = new ConcurrentHashMap<>();
    private volatile int timeoutThreshold = 0;

    public static DeterministicTargetExecutionStub alwaysSucceed() {
        DeterministicTargetExecutionStub stub = new DeterministicTargetExecutionStub();
        stub.useAlwaysSucceed();
        return stub;
    }

    public void useAlwaysSucceed() {
        defaultMode.set(Mode.SUCCESS);
        perKeyMode.clear();
    }

    public void useAlwaysPermanentFailure() {
        defaultMode.set(Mode.PERMANENT_FAILURE);
        perKeyMode.clear();
    }

    public void useAlwaysProviderTimeout() {
        defaultMode.set(Mode.ALWAYS_TIMEOUT);
        perKeyMode.clear();
    }

    /**
     * 같은 key(요청 input 텍스트)에 대해 처음 {@code n}회는 {@code PROVIDER_TIMEOUT}을
     * 반환하고, 그 이후 호출부터는 성공한다. key를 특정하지 않고 모든 요청에 적용된다.
     */
    public void useTimeoutNTimesThenSucceed(int n) {
        defaultMode.set(Mode.TIMEOUT_N_TIMES_THEN_SUCCESS);
        this.timeoutThreshold = n;
        perKeyMode.clear();
    }

    /**
     * input 텍스트별로 다른 모드를 지정한다. {@code TIMEOUT_N_TIMES_THEN_SUCCESS}는
     * 모든 key에 공통으로 {@code timeoutThreshold}를 적용한다.
     */
    public void useMixed(Map<String, Mode> modeByInput, int timeoutThreshold) {
        perKeyMode.clear();
        perKeyMode.putAll(modeByInput);
        this.timeoutThreshold = timeoutThreshold;
    }

    /** 모든 invocation 계수와 모드를 초기화하고 기본 SUCCESS 모드로 되돌린다. */
    public void reset() {
        invocationCounts.clear();
        perKeyMode.clear();
        defaultMode.set(Mode.SUCCESS);
        timeoutThreshold = 0;
    }

    @Override
    public TargetExecutionResult execute(TargetExecutionRequest request) {
        String key = request.input();
        int attemptNumber = invocationCounts
                .computeIfAbsent(key, ignored -> new AtomicInteger(0))
                .incrementAndGet();

        Mode mode = perKeyMode.getOrDefault(key, defaultMode.get());
        return switch (mode) {
            case SUCCESS -> TargetExecutionResult.succeeded("stub-success-response");
            case PERMANENT_FAILURE -> TargetExecutionResult.failed(TargetFailureCode.TARGET_CONFIGURATION_INVALID);
            case ALWAYS_TIMEOUT -> TargetExecutionResult.failed(TargetFailureCode.PROVIDER_TIMEOUT);
            case TIMEOUT_N_TIMES_THEN_SUCCESS -> attemptNumber <= timeoutThreshold
                    ? TargetExecutionResult.failed(TargetFailureCode.PROVIDER_TIMEOUT)
                    : TargetExecutionResult.succeeded("stub-success-after-timeout");
        };
    }

    /**
     * 특정 key(요청 input 텍스트)에 대해 실제로 수행된 invocation 수를 반환한다.
     * SQS 재전달 횟수와 달리, 이 값은 claim을 획득해 실제로 이 Stub까지 도달한 호출만 계수한다.
     */
    public int invocationCountFor(String key) {
        AtomicInteger counter = invocationCounts.get(key);
        return counter == null ? 0 : counter.get();
    }

    public int totalInvocationCount() {
        return invocationCounts.values().stream().mapToInt(AtomicInteger::get).sum();
    }
}
