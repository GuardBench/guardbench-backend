package com.guardbench.architecture;

import static com.guardbench.architecture.GuardBenchArchitectureRules.COMMON_DEPENDENCIES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.COMMON_DOMAIN_TYPES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.DOMAIN_DEPENDENCIES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.DOMAIN_TYPE_OWNERSHIP;
import static com.guardbench.architecture.GuardBenchArchitectureRules.EVALUATION_DEPENDENCIES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.EVALUATION_TESTRUN_BOUNDARY;
import static com.guardbench.architecture.GuardBenchArchitectureRules.GUARDRAIL_DEPENDENCIES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.PACKAGE_BY_DOMAIN;
import static com.guardbench.architecture.GuardBenchArchitectureRules.TESTDEFINITION_DEPENDENCIES;
import static com.guardbench.architecture.GuardBenchArchitectureRules.TESTRUN_DEPENDENCIES;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 승인된 package-by-domain 구조와 도메인 의존 경계를 production class에서 검증한다.
 *
 * @see <a href="../../../../../../docs/conventions/package-structure.md">패키지 구조와 네이밍</a>
 * @see <a href="../../../../../../docs/decisions/0006-independent-domain-contract-boundaries.md">ADR 0006</a>
 */
class GuardBenchArchitectureTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importProductionClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.guardbench");
    }

    @Test
    @DisplayName("production 클래스는 승인된 package-by-domain 구조를 따른다")
    void productionClassesFollowPackageByDomain() {
        PACKAGE_BY_DOMAIN.check(productionClasses);
    }

    @Test
    @DisplayName("Domain 클래스는 Spring MVC, JPA, AWS SDK 및 Presentation 타입에 의존하지 않는다")
    void domainClassesDoNotDependOnFrameworksOrPresentation() {
        DOMAIN_DEPENDENCIES.check(productionClasses);
    }

    @Test
    @DisplayName("testdefinition은 하위 Bounded Context 패키지에 의존하지 않는다")
    void testDefinitionDoesNotDependOnDownstreamContexts() {
        TESTDEFINITION_DEPENDENCIES.check(productionClasses);
    }

    @Test
    @DisplayName("testrun은 Integration Adapter 밖에서 다른 Bounded Context 패키지에 의존하지 않는다")
    void testRunDependsOnOtherContextsOnlyInsideIntegrationAdapters() {
        TESTRUN_DEPENDENCIES.check(productionClasses);
    }

    @Test
    @DisplayName("evaluation은 guardrail 패키지에 의존하지 않는다")
    void evaluationDoesNotDependOnGuardrail() {
        EVALUATION_DEPENDENCIES.check(productionClasses);
    }

    @Test
    @DisplayName("ADR 0006: evaluation은 testrun domain/infrastructure에 의존하지 않는다")
    void evaluationDoesNotDependOnTestRunDomainOrInfrastructure() {
        EVALUATION_TESTRUN_BOUNDARY.check(productionClasses);
    }

    @Test
    @DisplayName("guardrail은 evaluation 패키지에 의존하지 않는다")
    void guardrailDoesNotDependOnEvaluation() {
        GUARDRAIL_DEPENDENCIES.check(productionClasses);
    }

    @Test
    @DisplayName("common은 도메인 패키지에 의존하지 않는다")
    void commonDoesNotDependOnDomainPackages() {
        COMMON_DEPENDENCIES.check(productionClasses);
    }

    @Test
    @DisplayName("경계 간 공유하지 않는 Domain 타입은 승인된 패키지에서 소유한다")
    void domainTypesHaveOneApprovedOwner() {
        DOMAIN_TYPE_OWNERSHIP.check(productionClasses);
    }

    @Test
    @DisplayName("common 패키지는 Domain 타입을 소유하지 않는다")
    void commonDoesNotStoreDomainTypes() {
        COMMON_DOMAIN_TYPES.check(productionClasses);
    }
}
