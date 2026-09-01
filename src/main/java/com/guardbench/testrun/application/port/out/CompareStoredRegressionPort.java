package com.guardbench.testrun.application.port.out;

import java.util.List;

public interface CompareStoredRegressionPort {

    List<RegressionChangeView> compare(List<RegressionCaseInput> cases);
}
