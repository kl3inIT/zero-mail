package com.zeromail.core.admin.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import com.zeromail.core.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdminContextMutexTest {

    @Test
    void admin_context_rejects_tenant_context_lookup_inside_admin_scope() {
        AdminUser adminUser =
                new AdminUser(
                        UUID.fromString("00000000-0000-4000-8000-000000000801"),
                        "admin@example.com",
                        AdminStatus.ACTIVE,
                        Optional.of("Admin User"));

        assertThatIllegalStateException()
                .isThrownBy(() -> AdminContext.run(adminUser, TenantContext::currentOrThrow))
                .withMessageContaining("mutex")
                .withMessageContaining("admin scope");
    }

    @Test
    void admin_packages_do_not_depend_on_tenant_context_directly() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAnyPackage("..core.admin..")
                        .and()
                        .haveSimpleNameNotEndingWith("AdminTenantAccess")
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName("com.zeromail.core.tenant.TenantContext")
                        .because(
                                "admin flows must use AdminContext and the explicit future AdminTenantAccess read-only bridge")
                        .allowEmptyShould(true);

        rule.check(importProductionClasses());
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
