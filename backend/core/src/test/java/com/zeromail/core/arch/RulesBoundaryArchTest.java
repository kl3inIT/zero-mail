package com.zeromail.core.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

class RulesBoundaryArchTest {

  @Test
  void core_rules_does_not_import_spring_ai_or_vendor_sdks() {
    JavaClasses importedClasses = importProductionClasses();

    noClasses()
        .that()
        .resideInAPackage("..core.rules..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage("org.springframework.ai..", "com.openai..", "com.anthropic..")
        .because(
            "RULE-02: core.rules may call only the project LlmGateway; Spring AI and vendor SDK "
                + "imports stay confined to core.llm.gateway.springai.")
        .allowEmptyShould(true)
        .check(importedClasses);
  }

  @Test
  void core_rules_does_not_import_gmail_write_or_execution_packages() {
    JavaClasses importedClasses = importProductionClasses();

    noClasses()
        .that()
        .resideInAPackage("..core.rules..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..core.gmail.write..",
            "..core.gmail.execution..",
            "..core.triage.execution..",
            "..core.triage.actions..")
        .because(
            "RULE-05/RULE-09: Phase 03 preview stores and evaluates rules only; Gmail writes and "
                + "Phase 04 action execution must not be dependencies of core.rules.")
        .allowEmptyShould(true)
        .check(importedClasses);
  }

  private static JavaClasses importProductionClasses() {
    return new ClassFileImporter()
        .withImportOption(new ImportOption.DoNotIncludeTests())
        .importPackages("com.zeromail");
  }
}
