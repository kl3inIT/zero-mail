package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class LlmGatewayBoundaryTest {

    @Test
    void spring_ai_only_in_gateway_springai() {
        JavaClasses importedClasses = importProductionClasses();

        noClasses()
                .that()
                .resideOutsideOfPackages(
                        "..core.llm.gateway.springai..", "..core.chat.llm.springai..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.ai..")
                .because(
                        "LLM-01: Spring AI imports MUST be confined to core.llm.gateway.springai. "
                                + "LlmGatewayImpl depends only on the pure-Java LlmModelClient seam; "
                                + "SpringAiLlmModelClient is the single adapter that imports Spring AI types. "
                                + "NO EXEMPTION.")
                .check(importedClasses);
    }

    @Test
    void vendor_sdks_only_in_gateway_springai() {
        JavaClasses importedClasses = importProductionClasses();

        noClasses()
                .that()
                .resideOutsideOfPackages(
                        "..core.llm.gateway.springai..", "..core.chat.llm.springai..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("com.openai..", "com.anthropic..")
                .because(
                        "LLM-01: vendor SDK imports MUST be confined to core.llm.gateway.springai. "
                                + "NO EXEMPTION.")
                .check(importedClasses);
    }

    @Test
    void llm_runtime_routing_does_not_depend_on_admin_catalog() {
        JavaClasses importedClasses = importProductionClasses();

        noClasses()
                .that()
                .resideInAPackage("..core.llm..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..core.admin..")
                .because(
                        "runtime LLM routing is owned by core.llm.routing; admin may adapt to it, "
                                + "but core.llm must not import admin catalog types.")
                .check(importedClasses);
    }

    @Test
    void jsoup_and_jtokkit_only_in_gateway_sanitization() {
        JavaClasses importedClasses = importProductionClasses();

        noClasses()
                .that()
                .resideOutsideOfPackage("..core.llm.gateway.sanitization..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.jsoup..", "com.knuddels.jtokkit..")
                .because(
                        "LLM-05: HTML and tokenizer dependencies must stay inside the sanitization pipeline.")
                .check(importedClasses);
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
