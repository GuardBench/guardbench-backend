package com.guardbench.testrun.application.port.out;

public record RegressionChangeView(
        long testCaseId,
        String comparabilityStatus,
        String changeType) {
}
