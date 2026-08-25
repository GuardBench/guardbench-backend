package com.guardbench.testrun.application.port.out;

import com.guardbench.testrun.domain.TestRunId;

/**
 * TestRun 생성 전에 PostgreSQL sequence에서 식별자를 확보하는 consumer-owned Port다.
 */
public interface NextTestRunIdPort {

    TestRunId nextId();
}
