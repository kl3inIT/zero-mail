package com.zeromail.core.rules.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record RulePreviewCommand(
    UUID tenantId,
    UUID ruleId,
    MatcherNode matcherNode,
    List<ActionIntent> actionIntents,
    Integer requestedSampleSize) {

  public RulePreviewCommand {
    Objects.requireNonNull(tenantId, "tenantId must not be null");
    actionIntents = actionIntents == null ? List.of() : List.copyOf(actionIntents);
    boolean savedRulePreview = ruleId != null;
    boolean draftPreview = matcherNode != null && !actionIntents.isEmpty();
    if (savedRulePreview == draftPreview) {
      throw new IllegalArgumentException(
          "Preview command must target either a saved rule or a draft matcher/action payload");
    }
  }

  public static RulePreviewCommand savedRule(
      UUID tenantId, UUID ruleId, Integer requestedSampleSize) {
    return new RulePreviewCommand(tenantId, ruleId, null, List.of(), requestedSampleSize);
  }

  public static RulePreviewCommand draft(
      UUID tenantId,
      MatcherNode matcherNode,
      List<ActionIntent> actionIntents,
      Integer requestedSampleSize) {
    return new RulePreviewCommand(tenantId, null, matcherNode, actionIntents, requestedSampleSize);
  }

  public boolean savedRulePreview() {
    return ruleId != null;
  }
}
