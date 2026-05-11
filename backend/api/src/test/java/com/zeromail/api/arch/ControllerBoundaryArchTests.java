package com.zeromail.api.arch;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * WR-01 enforcement: controllers must NOT depend on the persistence module directly.
 * Tenant-sensitive flows (account, onboarding, Gmail connection state) belong inside application
 * services where transaction boundaries and tenant invariants live in one place.
 *
 * <p>Repository / entity access from {@code ..api.controllers..} is forbidden — controllers stay
 * transport-only and delegate to {@code ..core.account..} (or future service modules).
 */
@AnalyzeClasses(packages = "com.zeromail", importOptions = ImportOption.DoNotIncludeTests.class)
public class ControllerBoundaryArchTests {

    private static final DescribedPredicate<JavaClass> persistenceEntity =
            new DescribedPredicate<JavaClass>("be a persistence entity") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getSimpleName().endsWith("Entity");
                }
            }.and(
                    resideInAnyPackage(
                            "..core.account.persistence..",
                            "..core.gmail.persistence..",
                            "..core.onboarding.persistence..",
                            "..core.tenant.persistence.."));

    @ArchTest
    static final ArchRule controllers_do_not_touch_repositories =
            noClasses()
                    .that()
                    .resideInAPackage("..api.controllers..")
                    .should()
                    .dependOnClassesThat()
                    .haveNameMatching(".*Repository")
                    .because(
                            "WR-01: controllers must delegate to application services, "
                                    + "not call repositories directly");

    @ArchTest
    static final ArchRule controllers_do_not_touch_entities =
            noClasses()
                    .that()
                    .resideInAPackage("..api.controllers..")
                    .should()
                    .dependOnClassesThat(persistenceEntity)
                    .because(
                            "WR-01: controllers expose DTOs only; entity types stay behind "
                                    + "the service layer to keep tenant + transaction invariants in one place");
}
