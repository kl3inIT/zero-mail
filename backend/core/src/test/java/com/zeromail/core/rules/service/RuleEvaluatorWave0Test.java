package com.zeromail.core.rules.service;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class RuleEvaluatorWave0Test {

  private static final String PLAN_03_04_EVALUATOR_MESSAGE =
      "Plan 03-04 lands deterministic RuleEvaluator and tri-state result symbols";

  @Test
  @Disabled(PLAN_03_04_EVALUATOR_MESSAGE)
  void deterministic_matcher_families_return_stable_repeat_results_without_llm_invocation()
      throws Exception {
    Object evaluator = newFutureEvaluator();
    Object matcherAst = matcherAstFixtureCoveringDeterministicFamilies();
    Object previewMessage = previewMessageFixture();

    Object firstResult = evaluate(evaluator, matcherAst, previewMessage);
    Object secondResult = evaluate(evaluator, matcherAst, previewMessage);

    assertThat(firstResult).isEqualTo(secondResult);
    assertThat(failIfCalledLlmGatewayInvocationCount(evaluator)).isZero();
  }

  @Test
  @Disabled(PLAN_03_04_EVALUATOR_MESSAGE)
  void boolean_groups_support_all_any_and_not_semantics() throws Exception {
    Object evaluator = newFutureEvaluator();

    Object allResult = evaluate(evaluator, Map.of("type", "ALL", "children", java.util.List.of()), previewMessageFixture());
    Object anyResult = evaluate(evaluator, Map.of("type", "ANY", "children", java.util.List.of()), previewMessageFixture());
    Object notResult = evaluate(evaluator, Map.of("type", "NOT", "child", Map.of("type", "HAS_ATTACHMENT")), previewMessageFixture());

    assertThat(statusOf(allResult)).isIn("MATCHED", "NOT_MATCHED");
    assertThat(statusOf(anyResult)).isIn("MATCHED", "NOT_MATCHED");
    assertThat(statusOf(notResult)).isIn("MATCHED", "NOT_MATCHED");
  }

  @Test
  @Disabled(PLAN_03_04_EVALUATOR_MESSAGE)
  void semantic_intent_returns_deferred_at_the_evaluator_level_not_only_model_construction()
      throws Exception {
    Object evaluator = newFutureEvaluator();
    Object semanticIntentMatcher =
        Map.of(
            "type",
            "SEMANTIC_INTENT",
            "description",
            "messages that need a thoughtful reply",
            "deferred",
            true);

    Object evaluationResult = evaluate(evaluator, semanticIntentMatcher, previewMessageFixture());

    assertThat(statusOf(evaluationResult)).isEqualTo("DEFERRED");
    assertThat(failIfCalledLlmGatewayInvocationCount(evaluator)).isZero();
  }

  private static Object newFutureEvaluator() throws Exception {
    return Class.forName("com.zeromail.core.rules.service.RuleEvaluator")
        .getConstructor()
        .newInstance();
  }

  private static Object evaluate(Object evaluator, Object matcherAst, Object previewMessage)
      throws Exception {
    Method evaluateMethod = evaluator.getClass().getMethod("evaluate", Object.class, Object.class);
    return evaluateMethod.invoke(evaluator, matcherAst, previewMessage);
  }

  private static String statusOf(Object evaluationResult) throws Exception {
    Method statusMethod = evaluationResult.getClass().getMethod("status");
    Object status = statusMethod.invoke(evaluationResult);
    Method statusIdMethod = status.getClass().getMethod("id");
    return (String) statusIdMethod.invoke(status);
  }

  private static Object matcherAstFixtureCoveringDeterministicFamilies() {
    return Map.of(
        "type",
        "ALL",
        "children",
        java.util.List.of(
            Map.of("type", "SENDER_DOMAIN", "domain", "stripe.com"),
            Map.of("type", "RECIPIENT_TO", "email", "founder@example.test"),
            Map.of("type", "SUBJECT_CONTAINS", "value", "receipt"),
            Map.of("type", "GMAIL_LABEL_PRESENT", "labelId", "INBOX"),
            Map.of("type", "HAS_ATTACHMENT", "value", false),
            Map.of("type", "LIST_UNSUBSCRIBE_PRESENT", "value", true),
            Map.of("type", "MESSAGE_AGE", "operator", "LESS_THAN_DAYS", "days", 7)));
  }

  private static Object previewMessageFixture() {
    return Map.of(
        "senderEmail",
        "billing@stripe.com",
        "to",
        java.util.List.of("founder@example.test"),
        "subject",
        "Receipt from Stripe",
        "labelIds",
        java.util.List.of("INBOX"),
        "hasAttachment",
        false,
        "listUnsubscribePresent",
        true);
  }

  private static int failIfCalledLlmGatewayInvocationCount(Object evaluator) throws Exception {
    Method invocationCountMethod = evaluator.getClass().getMethod("llmInvocationCountForTest");
    return (Integer) invocationCountMethod.invoke(evaluator);
  }
}
