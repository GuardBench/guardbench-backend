package com.guardbench.architecture;

import static com.guardbench.architecture.GuardBenchArchitectureRules.COMMON_DOMAIN_TYPES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.DOMAIN_DEPENDENCIES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.PACKAGE_BY_DOMAIN;
import static com.guardbench.architecture.GuardBenchArchitectureRules.TESTRUN_DEPENDENCIES;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 의도적으로 격리한 위반 fixture를 사용해 각 ArchUnit 규칙의 탐지력과 실패 보고서를 검증한다.
 *
 * @see <a href="../../../../../../docs/conventions/test-code.md">테스트 코드 작성 지침</a>
 */
class GuardBenchArchitectureRuleDetectionTest {

    private final ClassFileImporter importer = new ClassFileImporter();

    @Test
    @DisplayName("전역 기술 패키지를 package-by-domain 위반으로 탐지한다")
    void packageByDomainRuleDetectsGlobalTechnicalPackage() {
        JavaClasses fixture = importer.importPackages("com.guardbench.controller.architecturefixture");

        assertViolation(PACKAGE_BY_DOMAIN, fixture, "outside the approved package-by-domain roots");
    }

    @Test
    @DisplayName("Domain의 Spring HTTP 의존을 금지 의존으로 탐지한다")
    void domainRuleDetectsSpringHttpDependency() {
        JavaClasses fixture = importer.importPackages("com.guardbench.testrun.domain.architecturefixture");

        assertViolation(DOMAIN_DEPENDENCIES, fixture, "org.springframework.http.ResponseEntity");
    }

    @Test
    @DisplayName("testrun Core에서 다른 Bounded Context로 향하는 의존을 탐지한다")
    void dependencyDirectionRuleDetectsTestRunCoreToAnotherBoundedContext() {
        JavaClasses fixture = importer.importPackages(
                "com.guardbench.testrun.application.architecturefixture",
                "com.guardbench.testdefinition.domain.architecturefixture"
        );

        assertViolation(TESTRUN_DEPENDENCIES, fixture, "TestDefinitionMarker");
    }

    @Test
    @DisplayName("testrun Presentation에서 다른 Bounded Context로 향하는 의존을 탐지한다")
    void dependencyDirectionRuleDetectsTestRunPresentationToAnotherBoundedContext() {
        JavaClasses fixture = importer.importPackages(
                "com.guardbench.testrun.presentation.architecturefixture",
                "com.guardbench.evaluation.domain.architecturefixture"
        );

        assertViolation(TESTRUN_DEPENDENCIES, fixture, "EvaluationMarker");
    }

    @Test
    @DisplayName("승인된 Integration Adapter 패키지의 경계 간 의존은 위반으로 보지 않는다")
    void dependencyDirectionRuleAllowsApprovedIntegrationAdapter() {
        JavaClasses fixture = importer.importPackages(
                "com.guardbench.testrun.infrastructure.integration.architecturefixture",
                "com.guardbench.evaluation.domain.architecturefixture"
        );

        assertNoViolation(TESTRUN_DEPENDENCIES, fixture);
    }


    @Test
    @DisplayName("common.domain의 타입을 Domain 소유권 위반으로 탐지한다")
    void commonRuleDetectsDomainType() {
        JavaClasses fixture = importer.importPackages("com.guardbench.common.domain.architecturefixture");

        assertViolation(COMMON_DOMAIN_TYPES, fixture, "stores a domain type under common");
    }

    private static void assertNoViolation(ArchRule rule, JavaClasses classes) {
        EvaluationResult result = rule.evaluate(classes);

        assertFalse(
                result.hasViolation(),
                () -> "Expected no violation for rule '" + rule.getDescription() + "' but was:\n"
                        + result.getFailureReport()
        );
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
