package com.zeromail.core.billing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class BillingDomainBoundaryArchTest {

    @Test
    void jdbc_template_only_in_lowlevel() {
        var importedClasses =
                new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .importPackages("com.zeromail");

        noClasses()
                .that()
                .resideInAPackage("..core.billing..")
                .and()
                .resideOutsideOfPackage("..core.billing.persistence.lowlevel..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.jdbc.core..")
                .because("D-G3: raw JDBC for billing is isolated to persistence.lowlevel")
                .check(importedClasses);
    }

    @Test
    void credit_ledger_service_not_instantiated_outside_billing_usecases() {
        var importedClasses =
                new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .importPackages("com.zeromail");

        noClasses()
                .that()
                .resideOutsideOfPackage("..core.billing.usecases..")
                .should()
                .dependOnClassesThat()
                .haveFullyQualifiedName("com.zeromail.core.billing.usecases.CreditLedgerService")
                .because("callers depend on CreditLedger, not the package-private implementation")
                .check(importedClasses);
    }

    @Test
    void core_billing_only_depends_on_allowed_packages() {
        var importedClasses =
                new ClassFileImporter()
                        .withImportOption(new ImportOption.DoNotIncludeTests())
                        .importPackages("com.zeromail");

        classes()
                .that()
                .resideInAPackage("..core.billing..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage(
                        "..core.billing..",
                        "..core.config..",
                        "..core.tenant..",
                        "..core.shared.persistence..",
                        "..core.shared.lang..",
                        "java..",
                        "jakarta..",
                        "org.springframework..",
                        "org.hibernate..",
                        "org.slf4j..",
                        "tools.jackson..")
                .because("D-G1: billing is a leaf module with a narrow dependency list")
                .check(importedClasses);
    }
}
