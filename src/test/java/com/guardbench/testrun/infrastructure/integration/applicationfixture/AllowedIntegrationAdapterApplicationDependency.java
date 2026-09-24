package com.guardbench.testrun.infrastructure.integration.applicationfixture;

import com.guardbench.testdefinition.application.architecturefixture.TestDefinitionApplicationMarker;

public final class AllowedIntegrationAdapterApplicationDependency {

    private TestDefinitionApplicationMarker marker;

    public TestDefinitionApplicationMarker marker() {
        return marker;
    }
}
