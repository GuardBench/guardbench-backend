package com.guardbench.testrun.application.port.out;

import com.guardbench.testrun.domain.Action;

public record RegressionCaseInput(
        long testCaseId,
        Action expectedAction,
        Action comparisonVerdict,
        Action currentVerdict) {
}
