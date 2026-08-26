package com.guardbench.testrun.application.port.out;

/**
 * TestRun 접수 시점에 대상 TestSuite의 존재 여부만 확인하는 consumer-owned 아웃바운드 Port다.
 *
 * <p>{@link LoadTestCaseSnapshotSourcesPort}는 활성 TestCase 목록만 반환하므로 빈 목록만으로는
 * "TestSuite가 없음"과 "TestSuite는 있지만 활성 TestCase가 없음"을 구분할 수 없다. 이 Port가 존재
 * 여부를 먼저 판정해 {@code 404 TEST_SUITE_NOT_FOUND}와 {@code 409 TEST_SUITE_EMPTY}를 구분한다.
 */
public interface ExistsTestSuitePort {

    boolean existsBySourceTestSuiteId(long sourceTestSuiteId);
}
