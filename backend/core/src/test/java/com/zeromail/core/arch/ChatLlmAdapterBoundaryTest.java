package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ChatLlmAdapterBoundaryTest {

    @Test
    void spring_ai_imports_in_chat_are_confined_to_chat_springai_adapter() {
        JavaClasses importedClasses = importProductionClasses();

        noClasses()
                .that()
                .resideInAPackage("..core.chat..")
                .and()
                .resideOutsideOfPackage("..core.chat.llm.springai..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("org.springframework.ai..")
                .because(
                        "ARCH-07: chat use cases stay Spring-AI-free; Spring AI imports are "
                                + "confined to core.chat.llm.springai")
                .allowEmptyShould(true)
                .check(importedClasses);
    }

    private static JavaClasses importProductionClasses() {
        return new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.zeromail");
    }
}
