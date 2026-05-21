package com.zeromail.core.admin.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.zeromail.core.admin.mkey.persistence.LlmProviderMasterKeyRepository;
import org.junit.jupiter.api.Test;

class MasterKeyResolverConfinementTest {

    @Test
    void only_authorised_callers_depend_on_master_key_repository() {
        // Phase B v2 split key access across three legitimate consumers:
        //   - core.llm.gateway.springai.admin (cipher decrypt + resolver cache)
        //   - core.admin.mkey.usecases        (admin CRUD: addKey, reorderKeys, revokeKey, ...)
        //   - core.admin.cat.usecases         (LlmRouter walks ACTIVE keys per tier)
        // Anything else touching the repo is still suspicious — the encrypted material must stay
        // confined to the resolver, and write paths must stay confined to the admin services.
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..core.llm.gateway.springai.admin..")
                        .and()
                        .resideOutsideOfPackage("..core.admin.mkey.usecases..")
                        .and()
                        .resideOutsideOfPackage("..core.admin.cat.usecases..")
                        .and()
                        .areNotAssignableTo(LlmProviderMasterKeyRepository.class)
                        .should()
                        .dependOnClassesThat()
                        .areAssignableTo(LlmProviderMasterKeyRepository.class)
                        .because(
                                "Only the resolver and the admin mkey/cat use-case packages may"
                                        + " touch llm_provider_master_key directly")
                        .allowEmptyShould(true);

        rule.check(importProductionClasses());
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
