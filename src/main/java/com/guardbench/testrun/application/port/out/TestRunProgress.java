package com.guardbench.testrun.application.port.out;

public record TestRunProgress(long processedTestCaseCount, double percent) {
    public TestRunProgress {
        if (processedTestCaseCount < 0 || percent < 0.0 || percent > 100.0) {
            throw new IllegalArgumentException("invalid TestRun progress");
        }
    }
}
