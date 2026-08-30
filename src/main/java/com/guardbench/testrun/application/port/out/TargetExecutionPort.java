package com.guardbench.testrun.application.port.out;

public interface TargetExecutionPort {

    TargetExecutionResult execute(TargetExecutionRequest request);
}
