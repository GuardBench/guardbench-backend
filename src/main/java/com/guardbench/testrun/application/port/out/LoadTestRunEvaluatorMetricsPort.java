package com.guardbench.testrun.application.port.out;

public interface LoadTestRunEvaluatorMetricsPort {

    EvaluatorMetricsView load(long testRunId);
}
