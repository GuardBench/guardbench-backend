package com.guardbench.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.Map;
import java.util.Set;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

final class GuardBenchArchitectureRules {

    private static final String BASE_PACKAGE = "com.guardbench";

    private static final Set<String> TOP_LEVEL_PACKAGES = Set.of(
            "testdefinition",
            "testrun",
            "evaluation",
            "evaluator",
            "target",
            "common"
    );

    private static final Map<String, String> OWNED_TYPE_PACKAGES = Map.ofEntries(
            Map.entry("TestSuite", "com.guardbench.testdefinition.domain"),
            Map.entry("TestSuiteId", "com.guardbench.testdefinition.domain"),
            Map.entry("TestCase", "com.guardbench.testdefinition.domain"),
            Map.entry("TestCaseId", "com.guardbench.testdefinition.domain"),
            Map.entry("TestSuiteRepository", "com.guardbench.testdefinition.domain.repository"),
            Map.entry("TestCaseRepository", "com.guardbench.testdefinition.domain.repository"),
            Map.entry("TestRun", "com.guardbench.testrun.domain"),
            Map.entry("TestRunId", "com.guardbench.testrun.domain"),
            Map.entry("TestCaseSnapshot", "com.guardbench.testrun.domain"),
            Map.entry("TestCaseSnapshotId", "com.guardbench.testrun.domain"),
            Map.entry("TestExecution", "com.guardbench.testrun.domain"),
            Map.entry("ApplicationResponse", "com.guardbench.testrun.domain"),
            Map.entry("EvaluationResult", "com.guardbench.testrun.domain"),
            Map.entry("TestRunExecutionOutcome", "com.guardbench.testrun.domain"),
            Map.entry("QualityGatePolicy", "com.guardbench.testrun.domain"),
            Map.entry("TestRunRepository", "com.guardbench.testrun.domain.repository"),
            Map.entry("TestCaseSnapshotRepository", "com.guardbench.testrun.domain.repository"),
            Map.entry("AssertionResult", "com.guardbench.evaluation.domain"),
            Map.entry("ChangeResult", "com.guardbench.evaluation.domain"),
            Map.entry("QualityGateMetric", "com.guardbench.evaluation.domain"),
            Map.entry("QualityGateResult", "com.guardbench.evaluation.domain"),
            Map.entry("AssertionStatus", "com.guardbench.evaluation.domain"),
            Map.entry("ComparabilityStatus", "com.guardbench.evaluation.domain"),
            Map.entry("ChangeType", "com.guardbench.evaluation.domain"),
            Map.entry("QualityGateStatus", "com.guardbench.evaluation.domain")
    );

    static final ArchRule PACKAGE_BY_DOMAIN = classes()
            .should(resideInApprovedTopLevelPackage())
            .as("production classes must follow the approved package-by-domain structure");

    static final ArchRule DOMAIN_DEPENDENCIES = noClasses()
            .that().resideInAPackage("com.guardbench..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.web..",
                    "org.springframework.http..",
                    "jakarta.persistence..",
                    "javax.persistence..",
                    "software.amazon.awssdk..",
                    "com.guardbench..presentation.."
            )
            .as("domain classes must not depend on Spring MVC, JPA, AWS SDK, or presentation types")
            .allowEmptyShould(true);

    static final ArchRule TESTDEFINITION_DEPENDENCIES = noClasses()
            .that().resideInAPackage("com.guardbench.testdefinition..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.guardbench.testrun..",
                    "com.guardbench.evaluation..",
                    "com.guardbench.evaluator..",
                    "com.guardbench.target.."
            )
            .as("testdefinition must not depend on downstream domains")
            .allowEmptyShould(true);

    static final ArchRule TESTRUN_DEPENDENCIES = noClasses()
            .that().resideInAPackage("com.guardbench.testrun..")
            .and().resideOutsideOfPackage("com.guardbench.testrun.infrastructure.integration..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.guardbench.testdefinition..",
                    "com.guardbench.evaluation..",
                    "com.guardbench.target.."
            )
            .as("testrun must not depend on other bounded contexts outside its integration adapters")
            .allowEmptyShould(true);

    static final ArchRule EVALUATION_DEPENDENCIES = noClasses()
            .that().resideInAPackage("com.guardbench.evaluation..")
            .should().dependOnClassesThat().resideInAPackage("com.guardbench.target..")
            .as("evaluation must not depend on guardrail")
            .allowEmptyShould(true);

    /**
     * ADR 0006: evaluation Integration Adapter는 testrun Application Facade만 호출한다.
     * testrun.domain 또는 testrun.domain.repository를 직접 import하지 않는다.
     */
    static final ArchRule EVALUATION_TESTRUN_BOUNDARY = noClasses()
            .that().resideInAPackage("com.guardbench.evaluation..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.guardbench.testrun.domain..",
                    "com.guardbench.testrun.infrastructure.."
            )
            .as("ADR 0006: evaluation must not depend on testrun domain or infrastructure; " +
                    "use testrun application facade via integration adapter")
            .allowEmptyShould(true);

    static final ArchRule GUARDRAIL_DEPENDENCIES = noClasses()
            .that().resideInAPackage("com.guardbench.target..")
            .should().dependOnClassesThat().resideInAPackage("com.guardbench.evaluation..")
            .as("guardrail must not depend on evaluation")
            .allowEmptyShould(true);

    static final ArchRule EVALUATOR_DEPENDENCIES = noClasses()
            .that().resideInAPackage("com.guardbench.evaluator..")
            .should().dependOnClassesThat().resideInAPackage("com.guardbench.evaluation..")
            .as("evaluator must not depend on evaluation")
            .allowEmptyShould(true);

    static final ArchRule COMMON_DEPENDENCIES = noClasses()
            .that().resideInAPackage("com.guardbench.common..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.guardbench.testdefinition..",
                    "com.guardbench.testrun..",
                    "com.guardbench.evaluation..",
                    "com.guardbench.evaluator..",
                    "com.guardbench.target.."
            )
            .as("common must not become a domain dependency hub")
            .allowEmptyShould(true);

    static final ArchRule DOMAIN_TYPE_OWNERSHIP = classes()
            .should(resideInOwnedPackage())
            .as("ADR 0001 domain types must exist only in their approved owner packages");

    static final ArchRule COMMON_DOMAIN_TYPES = classes()
            .should(notResideInCommonDomain())
            .as("common must not contain domain types");

    private GuardBenchArchitectureRules() {
    }

    private static ArchCondition<JavaClass> resideInApprovedTopLevelPackage() {
        return new ArchCondition<>("reside in an approved top-level package") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String packageName = javaClass.getPackageName();
                boolean rootApplication = packageName.equals(BASE_PACKAGE)
                        && javaClass.getSimpleName().equals("GuardbenchBackendApplication");
                boolean approvedPackage = TOP_LEVEL_PACKAGES.stream()
                        .map(topLevel -> BASE_PACKAGE + "." + topLevel)
                        .anyMatch(root -> packageName.equals(root) || packageName.startsWith(root + "."));

                if (!rootApplication && !approvedPackage) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            javaClass.getName() + " is outside the approved package-by-domain roots"
                    ));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> resideInOwnedPackage() {
        return new ArchCondition<>("reside in the package assigned by ADR 0001") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String expectedPackage = OWNED_TYPE_PACKAGES.get(javaClass.getSimpleName());
                if (expectedPackage != null && !javaClass.getPackageName().equals(expectedPackage)) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            javaClass.getName() + " must be owned by " + expectedPackage
                    ));
                }
            }
        };
    }

    private static ArchCondition<JavaClass> notResideInCommonDomain() {
        return new ArchCondition<>("not reside in common.domain") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                String packageName = javaClass.getPackageName();
                if (packageName.equals("com.guardbench.common.domain")
                        || packageName.startsWith("com.guardbench.common.domain.")) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            javaClass.getName() + " stores a domain type under common"
                    ));
                }
            }
        };
    }
}
