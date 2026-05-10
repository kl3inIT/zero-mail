package com.zeromail.api.dto.rules;

import java.util.List;

public record RulesListResponse(
    List<RuleResponse> rules,
    List<RuleTemplateResponse> templates,
    RuleTemplateMaterializationResponse materialization) {

  public RulesListResponse {
    rules = List.copyOf(rules);
    templates = List.copyOf(templates);
  }
}
