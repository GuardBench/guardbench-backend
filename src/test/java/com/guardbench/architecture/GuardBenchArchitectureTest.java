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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 승인된 package-by-domain 구조와 도메인 의존 경계를 production class에서 검증한다.
 *
 * @see <a href="../../../../../../docs/conventions/package-structure.md">패키지 구조와 네이밍</a>
 * @see <a href="../../../../../../docs/decisions/0001-domain-type-ownership-and-aggregate-boundaries.md">ADR 0001</a>
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
    @DisplayName("최상위 도메인 간 의존은 ADR 0001 방향을 따른다")
    void topLevelDomainDependenciesFollowAdr0001() {
        TESTDEFINITION_DEPENDENCIES.check(productionClasses);
        TESTRUN_DEPENDENCIES.check(productionClasses);
        EVALUATION_DEPENDENCIES.check(productionClasses);
        GUARDRAIL_DEPENDENCIES.check(productionClasses);
        COMMON_DEPENDENCIES.check(productionClasses);
    }

    @Test
    @DisplayName("ADR 0001 Domain 타입은 승인된 패키지 한 곳에서만 소유한다")
    void domainTypesHaveOneApprovedOwner() {
        DOMAIN_TYPE_OWNERSHIP.check(productionClasses);
    }

    @Test
    @DisplayName("common 패키지는 Domain 타입을 소유하지 않는다")
    void commonDoesNotStoreDomainTypes() {
        COMMON_DOMAIN_TYPES.check(productionClasses);
    }
}
