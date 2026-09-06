package com.guardbench.testrun.application;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.guardbench.testrun.application.port.out.LoadExecutionStatusesPort;
import com.guardbench.testrun.application.port.out.LockTestRunPort;
import com.guardbench.testrun.domain.TestRunExecutionSummary;
import com.guardbench.testrun.domain.TestRunId;
import com.guardbench.testrun.domain.TestRunStatus;
import com.guardbench.testrun.domain.repository.TestRunRepository;

/** Completion fan-in의 진행도와 readiness를 결과 전문 조회 없이 확인한다. */
@Service
public class CheckTestRunCompletionService {

    private final LockTestRunPort lockTestRunPort;
    private final TestRunRepository testRunRepository;
    private final LoadExecutionStatusesPort loadExecutionStatusesPort;
    private final Clock clock;

    public CheckTestRunCompletionService(
            LockTestRunPort lockTestRunPort,
            TestRunRepository testRunRepository,
            LoadExecutionStatusesPort loadExecutionStatusesPort,
            Clock clock
    ) {
        this.lockTestRunPort = lockTestRunPort;
        this.testRunRepository = testRunRepository;
        this.loadExecutionStatusesPort = loadExecutionStatusesPort;
        this.clock = clock;
    }

    /**
     * 부분 완료는 절대 진행도를 저장하고 false를 반환해 ACK한다.
     * terminal 결과는 불변이므로 이 트랜잭션 이후에도 readiness는 유지된다.
     * true이면 기존 최종화 트랜잭션에서 다시 잠그고 멱등성·원자성을 검증한다.
     * 이 확인 이후 실패하면 메시지를 ACK하지 않아 delivery retry가 최종화를 재시도한다.
     */
    @Transactional
    public boolean check(long testRunId) {
        if (!lockTestRunPort.lockForUpdate(testRunId)) {
            return false;
        }
        var run = testRunRepository.findById(new TestRunId(testRunId)).orElseThrow();
        if (run.status() == TestRunStatus.FINISHED) {
            return true;
        }
        if (run.status() != TestRunStatus.RUNNING) {
            return false;
        }
        var statuses = loadExecutionStatusesPort.load(run.id());
        if (statuses.size() != run.testCaseCount()) {
            return false;
        }
        var summary = TestRunExecutionSummary.from(statuses);
        if (summary.processedTestCaseCount() == summary.testCaseCount()) {
            return true;
        }
        run.updateProgress(summary, clock.instant());
        testRunRepository.save(run);
        return false;
    }
}
