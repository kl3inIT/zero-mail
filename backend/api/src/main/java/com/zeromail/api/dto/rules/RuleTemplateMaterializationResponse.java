package com.zeromail.api.dto.rules;

import java.util.List;

import com.zeromail.core.rules.application.RuleTemplateMaterializationResult;

public record RuleTemplateMaterializationResponse(
    int createdCount,
    int skippedCount,
    int customizedPreservedCount,
    List<RuleResponse> createdRules,
    List<SkippedTemplateResponse> skippedTemplates) {

  public static RuleTemplateMaterializationResponse empty() {
    return new RuleTemplateMaterializationResponse(0, 0, 0, List.of(), List.of());
  }

  public static RuleTemplateMaterializationResponse from(
      RuleTemplateMaterializationResult materializationResult) {
    return new RuleTemplateMaterializationResponse(
        materializationResult.createdCount(),
        materializationResult.skippedCount(),
        materializationResult.customizedPreservedCount(),
        materializationResult.createdRules().stream().map(RuleResponse::from).toList(),
        materializationResult.skippedTemplates().stream().map(SkippedTemplateResponse::from).toList());
  }

  public RuleTemplateMaterializationResponse {
    createdRules = List.copyOf(createdRules);
    skippedTemplates = List.copyOf(skippedTemplates);
  }
}
