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
    void only_provider_master_key_resolver_depends_on_master_key_repository_for_reads() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..core.llm.gateway.springai.admin..")
                        .and()
                        .areNotAssignableTo(LlmProviderMasterKeyRepository.class)
                        .should()
                        .dependOnClassesThat()
                        .areAssignableTo(LlmProviderMasterKeyRepository.class)
                        .because(
                                "ProviderMasterKeyResolver is the single reader for llm_provider_master_key")
                        .allowEmptyShould(true);

        rule.check(importProductionClasses());
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
