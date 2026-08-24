package com.guardbench.architecture;

import static com.guardbench.architecture.GuardBenchArchitectureRules.COMMON_DEPENDENCIES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.COMMON_DOMAIN_TYPES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.DOMAIN_DEPENDENCIES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.DOMAIN_TYPE_OWNERSHIP;
import static com.guardbench.architecture.GuardBenchArchitectureRules.EVALUATION_DEPENDENCIES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.GUARDRAIL_DEPENDENCIES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.PACKAGE_BY_DOMAIN;
import static com.guardbench.architecture.GuardBenchArchitectureRules.TESTDEFINITION_DEPENDENCIES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.TESTRUN_DEPENDENCIES;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GuardBenchArchitectureTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.guardbench");
    }

    @Test
    void productionClassesFollowPackageByDomain() {
        PACKAGE_BY_DOMAIN.check(productionClasses);
    }

    @Test
    void domainClassesDoNotDependOnFrameworksOrPresentation() {
        DOMAIN_DEPENDENCIES.check(productionClasses);
    }

    @Test
    void topLevelDomainDependenciesFollowAdr0001() {
        TESTDEFINITION_DEPENDENCIES.check(productionClasses);
        TESTRUN_DEPENDENCIES.check(productionClasses);
        EVALUATION_DEPENDENCIES.check(productionClasses);
        GUARDRAIL_DEPENDENCIES.check(productionClasses);
        COMMON_DEPENDENCIES.check(productionClasses);
    }

    @Test
    void domainTypesHaveOneApprovedOwner() {
        DOMAIN_TYPE_OWNERSHIP.check(productionClasses);
    }

    @Test
    void commonDoesNotStoreDomainTypes() {
        COMMON_DOMAIN_TYPES.check(productionClasses);
    }
}
