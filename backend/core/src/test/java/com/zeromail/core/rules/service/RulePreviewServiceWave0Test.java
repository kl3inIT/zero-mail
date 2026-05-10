package com.zeromail.core.rules.service;


import com.zeromail.core.llm.domain.Action;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class RulePreviewServiceWave0Test {

  private static final String PLAN_03_05_PREVIEW_MESSAGE =
      "Plan 03-05 lands RulePreviewService sample limits, privacy, and read-only preview behavior";
  private static final Pattern FORBIDDEN_WRITE_TYPE_NAME =
      Pattern.compile(
          "\\b(?:[A-Za-z0-9_.]*Gmail[A-Za-z0-9_]*Write[A-Za-z0-9_]*"
              + "|[A-Za-z0-9_.]*Action[A-Za-z0-9_]*Executor[A-Za-z0-9_]*"
              + "|[A-Za-z0-9_.]*Gmail[A-Za-z0-9_]*Executor[A-Za-z0-9_]*)\\b");

  @Test
  void preview_service_sources_do_not_depend_on_gmail_write_or_action_executor_types()
      throws Exception {
    Path rulesServiceRoot =
        findCoreProductionRoot().resolve("com/zeromail/core/rules/service");
    if (!Files.isDirectory(rulesServiceRoot)) {
      return;
    }

    try (Stream<Path> sourcePaths = Files.walk(rulesServiceRoot)) {
      List<Path> previewServiceSources =
          sourcePaths
              .filter(Files::isRegularFile)
              .filter(sourcePath -> sourcePath.getFileName().toString().contains("PreviewService"))
              .filter(sourcePath -> sourcePath.getFileName().toString().endsWith(".java"))
              .toList();

      for (Path previewServiceSource : previewServiceSources) {
        String sourceText = Files.readString(previewServiceSource);
        assertThat(FORBIDDEN_WRITE_TYPE_NAME.matcher(sourceText).find())
            .as(
                "Preview service %s must not declare Gmail write clients or action executors",
                previewServiceSource)
            .isFalse();
      }
    }
  }

  @Test
  @Disabled(PLAN_03_05_PREVIEW_MESSAGE)
  void preview_accepts_only_sample_sizes_10_25_and_50_with_default_25() throws Exception {
    Object previewService = newFuturePreviewService();
    Method normalizeMethod = previewService.getClass().getMethod("normalizeSampleSize", Integer.class);

    assertThat(normalizeMethod.invoke(previewService, (Integer) null)).isEqualTo(25);
    assertThat(normalizeMethod.invoke(previewService, 10)).isEqualTo(10);
    assertThat(normalizeMethod.invoke(previewService, 25)).isEqualTo(25);
    assertThat(normalizeMethod.invoke(previewService, 50)).isEqualTo(50);
    assertThatThrownBy(() -> normalizeMethod.invoke(previewService, 51))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @Disabled(PLAN_03_05_PREVIEW_MESSAGE)
  void preview_never_invokes_gmail_writes_or_action_execution() throws Exception {
    Object previewService =
        newFuturePreviewService(
            new FailIfCalledGmailWriteClient(), new FailIfCalledActionExecutor());
    Method previewMethod =
        previewService.getClass().getMethod("preview", Object.class, Integer.class);

    Object previewResult = previewMethod.invoke(previewService, savedRuleFixture(), 25);

    assertThat(previewResult).isNotNull();
  }

  @Test
  @Disabled(PLAN_03_05_PREVIEW_MESSAGE)
  void preview_result_and_persistence_exclude_raw_content_prompts_and_completions() throws Exception {
    Object previewService = newFuturePreviewService();
    Method previewMethod =
        previewService.getClass().getMethod("preview", Object.class, Integer.class);

    Object previewResult = previewMethod.invoke(previewService, savedRuleFixture(), 25);
    String serializedResult = String.valueOf(previewResult);

    assertThat(serializedResult)
        .doesNotContain("rawBody", "rawHeaders", "snippet", "prompt", "completion");
  }

  @Test
  @Disabled(PLAN_03_05_PREVIEW_MESSAGE)
  void preview_summary_includes_the_no_gmail_changes_notice() throws Exception {
    Object previewService = newFuturePreviewService();
    Method previewMethod =
        previewService.getClass().getMethod("preview", Object.class, Integer.class);

    Object previewResult = previewMethod.invoke(previewService, savedRuleFixture(), 25);

    assertThat(String.valueOf(previewResult)).contains("No Gmail changes were made.");
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

  private static Object newFuturePreviewService(Object... constructorArguments) throws Exception {
    Class<?> previewServiceClass = Class.forName("com.zeromail.core.rules.service.RulePreviewService");
    if (constructorArguments.length == 0) {
      return previewServiceClass.getConstructor().newInstance();
    }
    Class<?>[] constructorTypes =
        Stream.of(constructorArguments)
            .map(Object::getClass)
            .toArray(Class<?>[]::new);
    return previewServiceClass.getConstructor(constructorTypes).newInstance(constructorArguments);
  }

  private static Object savedRuleFixture() {
    return Map.of(
        "ruleId",
        "rule-1",
        "matcher",
        Map.of("type", "SENDER_DOMAIN", "domain", "stripe.com"),
        "actions",
        List.of(Map.of("type", "archive")));
  }

  private static final class FailIfCalledGmailWriteClient {

    void createLabel(String gmailMessageId, String labelName) {
      fail("Preview must not create Gmail labels for message " + gmailMessageId + " / " + labelName);
    }

    void archiveMessage(String gmailMessageId) {
      fail("Preview must not archive Gmail message " + gmailMessageId);
    }

    void saveDraft(String gmailThreadId) {
      fail("Preview must not save Gmail draft for thread " + gmailThreadId);
    }
  }

  private static final class FailIfCalledActionExecutor {

    void execute(Object actionIntent) {
      fail("Preview must not execute action intent " + actionIntent);
    }
  }
}
