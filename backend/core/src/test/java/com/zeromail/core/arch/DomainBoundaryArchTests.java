package com.zeromail.core.arch;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * D-D1 boundary enforcement: a class in {@code core.<domainA>} must NOT import any
 * {@code *Repository} from {@code core.<domainB>.persistence} where A != B.
 * Cross-domain reads MUST go through the owning domain's {@code @Service} class.
 *
 * <p>ArchUnit's fluent DSL has no "same-package-as-caller" predicate, so the rule
 * is expressed as four explicit per-domain ArchRules (Pitfall 4 in 01.2-RESEARCH.md).
 * Each new domain in Phase 2A/2B/2C/3/4 adds one more rule + extends the other rules'
 * exclusion arrays.
 *
 * <p>The {@code dependOnClassesThat(predicate)} overload is used because the fluent
 * builder ({@code .haveNameMatching(...).and().resideInAnyPackage(...)}) is not chainable
 * in ArchUnit 1.4.x — {@code haveNameMatching} returns {@code ClassesShouldConjunction}
 * which has no zero-arg {@code and()}. Predicate composition at the
 * {@link DescribedPredicate} level is the correct shape.
 */
@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
public class DomainBoundaryArchTests {

    private static final DescribedPredicate<JavaClass> nameEndsWithRepository =
            new DescribedPredicate<JavaClass>("have a name ending in 'Repository'") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getName().endsWith("Repository");
                }
            };

    @ArchTest
    static final ArchRule account_no_cross_domain_repos = noClasses()
            .that().resideInAPackage("..core.account..")
            .should().dependOnClassesThat(
                    nameEndsWithRepository.and(resideInAnyPackage(
                            "..core.onboarding.persistence..",
                            "..core.gmail.persistence..",
                            "..core.tenant.persistence..",
                            "..core.billing.persistence..",
                            "..core.llm.persistence..")))
            .because("D-D1: cross-domain reads must go through the other domain's Service");

    @ArchTest
    static final ArchRule onboarding_no_cross_domain_repos = noClasses()
            .that().resideInAPackage("..core.onboarding..")
            .should().dependOnClassesThat(
                    nameEndsWithRepository.and(resideInAnyPackage(
                            "..core.account.persistence..",
                            "..core.gmail.persistence..",
                            "..core.tenant.persistence..",
                            "..core.billing.persistence..",
                            "..core.llm.persistence..")))
            .because("D-D1: cross-domain reads must go through the other domain's Service");

    @ArchTest
    static final ArchRule gmail_no_cross_domain_repos = noClasses()
            .that().resideInAPackage("..core.gmail..")
            .should().dependOnClassesThat(
                    nameEndsWithRepository.and(resideInAnyPackage(
                            "..core.account.persistence..",
                            "..core.onboarding.persistence..",
                            "..core.tenant.persistence..",
                            "..core.billing.persistence..",
                            "..core.llm.persistence..")))
            .because("D-D1: cross-domain reads must go through the other domain's Service");

    @ArchTest
    static final ArchRule tenant_no_cross_domain_repos = noClasses()
            .that().resideInAPackage("..core.tenant..")
            .should().dependOnClassesThat(
                    nameEndsWithRepository.and(resideInAnyPackage(
                            "..core.account.persistence..",
                            "..core.onboarding.persistence..",
                            "..core.gmail.persistence..",
                            "..core.billing.persistence..",
                            "..core.llm.persistence..")))
            .because("D-D1: cross-domain reads must go through the other domain's Service");

    @ArchTest
    static final ArchRule billing_no_cross_domain_repos = noClasses()
            .that().resideInAPackage("..core.billing..")
            .should().dependOnClassesThat(
                    nameEndsWithRepository.and(resideInAnyPackage(
                            "..core.account.persistence..",
                            "..core.onboarding.persistence..",
                            "..core.gmail.persistence..",
                            "..core.tenant.persistence..")))
            .because("D-D1: cross-domain reads must go through the other domain's Service");

    @ArchTest
    static final ArchRule llm_no_cross_domain_repos = noClasses()
            .that().resideInAPackage("..core.llm..")
            .should().dependOnClassesThat(
                    nameEndsWithRepository.and(resideInAnyPackage(
                            "..core.account.persistence..",
                            "..core.onboarding.persistence..",
                            "..core.gmail.persistence..",
                            "..core.tenant.persistence..",
                            "..core.billing.persistence..")))
            .because("D-D1: cross-domain reads must go through the other domain's Service");
}
