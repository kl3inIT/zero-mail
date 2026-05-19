package com.zeromail.api.dto.rules;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"rules", "templates", "materialization"})
public record RulesListResponse(
        List<RuleResponse> rules,
        List<RuleTemplateResponse> templates,
        RuleTemplateMaterializationResponse materialization) {

    public RulesListResponse {
        rules = List.copyOf(rules);
        templates = List.copyOf(templates);
    }
}
