package com.zeromail.api.dto.rules;

import java.time.Instant;
import java.util.UUID;

import com.zeromail.core.rules.projection.RuleStatusProjection;

public record RuleResponse(
    UUID ruleId,
    String displayName,
    String sourceText,
    boolean enabled,
    int orderIndex,
    String sourceLanguage,
    String schemaVersion,
    String matcherAst,
    String actionIntents,
    Integer entityVersion,
    Integer lastPreviewedEntityVersion,
    Instant lastPreviewedAt,
    String templateKey,
    Integer templateVersion,
    boolean customized) {

  public static RuleResponse from(RuleStatusProjection ruleStatusProjection) {
    return new RuleResponse(
        ruleStatusProjection.ruleId().value(),
        ruleStatusProjection.displayName(),
        ruleStatusProjection.sourceText(),
        ruleStatusProjection.enabled(),
        ruleStatusProjection.orderIndex(),
        ruleStatusProjection.sourceLanguage().id(),
        ruleStatusProjection.schemaVersion().id(),
        ruleStatusProjection.matcherAst(),
        ruleStatusProjection.actionIntents(),
        ruleStatusProjection.entityVersion(),
        ruleStatusProjection.lastPreviewedEntityVersion(),
        ruleStatusProjection.lastPreviewedAt(),
        ruleStatusProjection.templateKey(),
        ruleStatusProjection.templateVersion(),
        ruleStatusProjection.customized());
  }
}
