package com.zeromail.core.admin.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.zeromail.core.admin.tenant.usecases.TenantOAuthRevocationGateway;
import org.junit.jupiter.api.Test;

class AdminTenantOAuthGuardTest {

    @Test
    void admin_tenant_code_uses_revocation_gateway_instead_of_token_bearing_gmail_internals() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("..controllers.admin..", "..core.admin..")
                        .and()
                        .areNotAssignableTo(TenantOAuthRevocationGateway.class)
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "..core.gmail.persistence..",
                                "..core.gmail.persistence.crypto..",
                                "..core.gmail.gateway..")
                        .because(
                                "admin tenant operations must never inject repositories or token-decrypting Gmail internals")
                        .allowEmptyShould(true);

        rule.check(importProductionClasses());
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
