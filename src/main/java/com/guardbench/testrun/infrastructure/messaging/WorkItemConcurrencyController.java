package com.guardbench.testrun.infrastructure.messaging;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * WorkItems 전용 bounded executor와 in-flight slot을 함께 관리한다.
 *
 * <p>수신 전에 slot을 예약하므로 executor queue에 수신한 메시지가 무제한으로 쌓이지 않는다.
 */
final class WorkItemConcurrencyController implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(WorkItemConcurrencyController.class);

    private final int concurrency;
    private final Duration shutdownTimeout;
    private final Semaphore slots;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicLong threadSequence = new AtomicLong();
    private final ThreadPoolExecutor executor;
    private volatile boolean accepting = true;
    private volatile boolean shutdownTimedOut;

    WorkItemConcurrencyController(int concurrency, Duration shutdownTimeout) {
        if (concurrency <= 0) {
            throw new IllegalArgumentException("WorkItems concurrency must be positive");
        }
        if (shutdownTimeout.isNegative() || shutdownTimeout.isZero()) {
            throw new IllegalArgumentException("WorkItems shutdown timeout must be positive");
        }
        this.concurrency = concurrency;
        this.shutdownTimeout = shutdownTimeout;
        this.slots = new Semaphore(concurrency, true);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "guardbench-work-item-" + threadSequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        this.executor = new ThreadPoolExecutor(
                concurrency,
                concurrency,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(concurrency),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
        log.info("WorkItems worker 동시성 설정을 적용합니다. concurrency={} shutdownTimeoutSeconds={}",
                concurrency, shutdownTimeout.toSeconds());
    }

    int reserveAvailableSlots(int requestedSlots) {
        if (!accepting) {
            return 0;
        }
        int slotsToReserve = Math.min(requestedSlots, slots.availablePermits());
        if (slotsToReserve == 0 || !slots.tryAcquire(slotsToReserve)) {
            return 0;
        }
        inFlight.addAndGet(slotsToReserve);
        return slotsToReserve;
    }

    boolean submit(Runnable task) {
        if (!accepting) {
            releaseSlot();
            return false;
        }
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } finally {
                    releaseSlot();
                }
            });
            return true;
        } catch (RejectedExecutionException exception) {
            releaseSlot();
            return false;
        }
    }

    void releaseSlots(int count) {
        for (int index = 0; index < count; index++) {
            releaseSlot();
        }
    }

    int concurrency() {
        return concurrency;
    }

    int currentInFlight() {
        return inFlight.get();
    }

    int availableSlots() {
        return slots.availablePermits();
    }

    boolean canAcknowledge() {
        return !shutdownTimedOut;
    }

    @Override
    @PreDestroy
    public void close() {
        accepting = false;
        executor.shutdown();
        try {
            if (executor.awaitTermination(shutdownTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
                return;
            }
            shutdownTimedOut = true;
            List<Runnable> droppedTasks = executor.shutdownNow();
            droppedTasks.forEach(ignored -> releaseSlot());
            log.warn("WorkItems worker 종료 대기 시간이 초과되어 미완료 작업을 중단했습니다. "
                            + "shutdownTimeoutSeconds={} currentInFlight={}",
                    shutdownTimeout.toSeconds(), currentInFlight());
        } catch (InterruptedException exception) {
            shutdownTimedOut = true;
            List<Runnable> droppedTasks = executor.shutdownNow();
            droppedTasks.forEach(ignored -> releaseSlot());
            Thread.currentThread().interrupt();
            log.warn("WorkItems worker 종료 대기 중 인터럽트가 발생했습니다. currentInFlight={}", currentInFlight());
        }
    }

    private void releaseSlot() {
        inFlight.decrementAndGet();
        slots.release();
    }
}
