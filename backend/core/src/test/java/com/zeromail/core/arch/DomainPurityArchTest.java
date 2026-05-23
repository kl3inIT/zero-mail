package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * Bright-line guard: every {@code com.zeromail.core.<context>.domain..} package is tactical-DDD
 * domain code and MUST stay framework-free. If a domain class needs Spring, Jackson, JPA, or
 * Hibernate it belongs in {@code usecases/} (Clean Architecture use-case layer) or an adapter
 * package, not in {@code domain/}. There is NO whitelist and NO exemption — a single forbidden
 * dependency fails the build.
 *
 * <p>The pattern {@code ..com.zeromail.core.*.domain..} matches a {@code <context>/domain/} segment
 * only; it deliberately does not match {@code core.shared.persistence..} or other shared
 * infrastructure packages, which are not domain code.
 */
class DomainPurityArchTest {

    @Test
    void context_domain_packages_are_framework_free() {
        JavaClasses importedClasses =
                new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .withImportOption(
                                location -> !location.toString().contains("package-info.class"))
                        .importPackages("com.zeromail");

        noClasses()
                .that()
                .resideInAPackage("..com.zeromail.core.*.domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "tools.jackson..",
                        "jakarta.persistence..",
                        "org.hibernate..")
                .because(
                        "Domain layer (tactical DDD) MUST be framework-free — if a class needs a"
                                + " framework dependency it belongs in usecases/, not domain/. NO"
                                + " WHITELIST, NO EXEMPTION.")
                .check(importedClasses);
    }
}
