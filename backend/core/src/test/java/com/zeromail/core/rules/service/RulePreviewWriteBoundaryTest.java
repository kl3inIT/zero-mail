package com.zeromail.core.rules.service;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.zeromail.core.gmail.service.GmailPreviewReadService;

class RulePreviewWriteBoundaryTest {

  private static final Pattern FORBIDDEN_TYPE_NAME =
      Pattern.compile(
          "\\b(?:[A-Za-z0-9_.]*Gmail[A-Za-z0-9_]*Write[A-Za-z0-9_]*"
              + "|[A-Za-z0-9_.]*Action[A-Za-z0-9_]*Executor[A-Za-z0-9_]*"
              + "|[A-Za-z0-9_.]*Gmail[A-Za-z0-9_]*Executor[A-Za-z0-9_]*)\\b");
  @Test
  void preview_classes_do_not_depend_on_gmail_write_or_phase_4_execution_packages() {
    JavaClasses importedClasses =
        new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.zeromail");

    noClasses()
        .that()
        .resideInAnyPackage("..core.rules.service..", "..core.gmail.service..")
        .and()
        .haveSimpleNameEndingWith("Service")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..core.gmail.write..",
            "..core.gmail.execution..",
            "..core.triage.execution..",
            "..core.triage.actions..")
        .because("rule preview is read-only and Phase 4 owns Gmail writes")
        .allowEmptyShould(false)
        .check(importedClasses);
  }

  @Test
  void preview_constructor_and_field_types_exclude_write_clients_and_action_executors() {
    for (Class<?> previewClass :
        List.of(
            RulePreviewService.class,
            RulePreviewDataService.class,
            GmailPreviewReadService.class)) {
      for (Field field : previewClass.getDeclaredFields()) {
        assertThat(FORBIDDEN_TYPE_NAME.matcher(field.getType().getName()).find())
            .as("%s field %s must stay read-only", previewClass.getSimpleName(), field.getName())
            .isFalse();
      }
      for (Constructor<?> constructor : previewClass.getDeclaredConstructors()) {
        for (Class<?> parameterType : constructor.getParameterTypes()) {
          assertThat(FORBIDDEN_TYPE_NAME.matcher(parameterType.getName()).find())
              .as(
                  "%s constructor parameter %s must stay read-only",
                  previewClass.getSimpleName(), parameterType)
              .isFalse();
        }
      }
    }
  }

  @Test
  void preview_sources_do_not_call_gmail_write_methods() throws Exception {
    Path productionRoot = findCoreProductionRoot();
    List<Path> previewSources;
    try (Stream<Path> sourcePaths = Files.walk(productionRoot.resolve("com/zeromail/core"))) {
      previewSources =
          sourcePaths
              .filter(Files::isRegularFile)
              .filter(sourcePath -> sourcePath.getFileName().toString().contains("Preview"))
              .filter(sourcePath -> sourcePath.getFileName().toString().endsWith(".java"))
              .toList();
    }

    for (Path previewSource : previewSources) {
      String sourceText = Files.readString(previewSource);
      assertThat(sourceText)
          .as("Preview source %s must not call Gmail write APIs", previewSource)
          .doesNotContain(".modify(", ".trash(", ".untrash(", ".send(", ".batchModify(")
          .doesNotContain(".batchDelete(", ".delete(");
    }
  }

  private static Path findCoreProductionRoot() {
    Path currentDirectory = Path.of("").toAbsolutePath();
    while (currentDirectory != null) {
      Path monorepoCoreRoot = currentDirectory.resolve("backend/core/src/main/java");
      if (Files.isDirectory(monorepoCoreRoot)) {
        return monorepoCoreRoot;
      }
      Path moduleCoreRoot = currentDirectory.resolve("src/main/java");
      if (Files.isDirectory(moduleCoreRoot)
          && "core".equals(currentDirectory.getFileName().toString())) {
        return moduleCoreRoot;
      }
      currentDirectory = currentDirectory.getParent();
    }
    throw new IllegalStateException("Could not locate backend/core/src/main/java");
  }
}
