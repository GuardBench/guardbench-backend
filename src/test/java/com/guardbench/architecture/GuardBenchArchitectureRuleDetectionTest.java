package com.guardbench.architecture;

import static com.guardbench.architecture.GuardBenchArchitectureRules.COMMON_DOMAIN_TYPES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.DOMAIN_DEPENDENCIES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.DOMAIN_TYPE_OWNERSHIP;
import static com.guardbench.architecture.GuardBenchArchitectureRules.PACKAGE_BY_DOMAIN;
import static com.guardbench.architecture.GuardBenchArchitectureRules.TESTRUN_DEPENDENCIES;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import org.junit.jupiter.api.Test;

class GuardBenchArchitectureRuleDetectionTest {

    private final ClassFileImporter importer = new ClassFileImporter();

    @Test
    void packageByDomainRuleDetectsGlobalTechnicalPackage() {
        JavaClasses fixture = importer.importPackages("com.guardbench.controller.architecturefixture");

        assertViolation(PACKAGE_BY_DOMAIN, fixture, "outside the approved package-by-domain roots");
    }

    @Test
    void domainRuleDetectsSpringHttpDependency() {
        JavaClasses fixture = importer.importPackages("com.guardbench.testrun.domain.architecturefixture");

        assertViolation(DOMAIN_DEPENDENCIES, fixture, "org.springframework.http.ResponseEntity");
    }

    @Test
    void dependencyDirectionRuleDetectsTestrunToEvaluation() {
        JavaClasses fixture = importer.importPackages(
                "com.guardbench.testrun.application.architecturefixture",
                "com.guardbench.evaluation.domain.architecturefixture"
        );

        assertViolation(TESTRUN_DEPENDENCIES, fixture, "EvaluationMarker");
    }

    @Test
    void ownershipRuleDetectsDuplicateAction() {
        JavaClasses fixture = importer.importPackages("com.guardbench.testrun.domain.architecturefixture");

        assertViolation(DOMAIN_TYPE_OWNERSHIP, fixture, "must be owned by com.guardbench.testdefinition.domain");
    }

    @Test
    void commonRuleDetectsDomainType() {
        JavaClasses fixture = importer.importPackages("com.guardbench.common.domain.architecturefixture");

        assertViolation(COMMON_DOMAIN_TYPES, fixture, "stores a domain type under common");
    }

    private static void assertViolation(ArchRule rule, JavaClasses classes, String expectedMessage) {
        EvaluationResult result = rule.evaluate(classes);
        String failureReport = result.getFailureReport().toString();

        assertTrue(result.hasViolation(), () -> "Expected violation for rule: " + rule.getDescription());
        assertTrue(
                failureReport.contains(rule.getDescription()),
                () -> "Failure report must identify rule '" + rule.getDescription() + "' but was:\n"
                        + failureReport
        );
        assertTrue(
                failureReport.contains(expectedMessage),
                () -> "Expected failure message containing '" + expectedMessage + "' but was:\n"
                        + failureReport
        );
    }
}
