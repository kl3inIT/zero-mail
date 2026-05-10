package com.zeromail.core.rules.application;


import com.zeromail.core.rules.domain.RuleLanguage;
import com.zeromail.core.rules.domain.RuleSchemaVersion;
public record RuleCompileResult(
    Status status,
    RuleLanguage sourceLanguage,
    String displayName,
    RuleSchemaVersion schemaVersion,
    String matcherAst,
    String actionIntents,
    RuleClarificationQuestion clarificationQuestion,
    String failureReason) {

  public enum Status {
    COMPILED,
    CLARIFICATION_REQUIRED,
    INVALID
  }

  public RuleCompileResult {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    sourceLanguage = sourceLanguage == null ? RuleLanguage.UNKNOWN : sourceLanguage;
    if (status == Status.COMPILED) {
      requireText(displayName, "displayName");
      if (schemaVersion == null) {
        throw new IllegalArgumentException("schemaVersion must not be null");
      }
      requireText(matcherAst, "matcherAst");
      requireText(actionIntents, "actionIntents");
      clarificationQuestion = null;
      failureReason = null;
    } else if (status == Status.CLARIFICATION_REQUIRED) {
      if (clarificationQuestion == null) {
        throw new IllegalArgumentException("clarificationQuestion must not be null");
      }
      displayName = null;
      schemaVersion = null;
      matcherAst = null;
      actionIntents = null;
      failureReason = null;
    } else {
      displayName = null;
      schemaVersion = null;
      matcherAst = null;
      actionIntents = null;
      clarificationQuestion = null;
      failureReason = failureReason == null || failureReason.isBlank() ? "invalid" : failureReason;
    }
  }

  public static RuleCompileResult compiled(
      RuleLanguage sourceLanguage,
      String displayName,
      RuleSchemaVersion schemaVersion,
      String matcherAst,
      String actionIntents) {
    return new RuleCompileResult(
        Status.COMPILED,
        sourceLanguage,
        displayName,
        schemaVersion,
        matcherAst,
        actionIntents,
        null,
        null);
  }

  public static RuleCompileResult requiresClarification(RuleClarificationQuestion question) {
    return new RuleCompileResult(
        Status.CLARIFICATION_REQUIRED,
        question.language(),
        null,
        null,
        null,
        null,
        question,
        null);
  }

  public static RuleCompileResult invalid(RuleLanguage sourceLanguage, String failureReason) {
    return new RuleCompileResult(
        Status.INVALID,
        sourceLanguage,
        null,
        null,
        null,
        null,
        null,
        failureReason);
  }

  public boolean isCompiled() {
    return status == Status.COMPILED;
  }

  public boolean requiresClarification() {
    return status == Status.CLARIFICATION_REQUIRED;
  }

  public boolean isInvalid() {
    return status == Status.INVALID;
  }

  private static void requireText(String text, String fieldName) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }
}
