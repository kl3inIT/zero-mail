package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RuleTemplateMaterializationResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(
        requiredProperties = {
            "createdCount",
            "skippedCount",
            "customizedPreservedCount",
            "createdRules",
            "skippedTemplates"
        })
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
                materializationResult.skippedTemplates().stream()
                        .map(SkippedTemplateResponse::from)
                        .toList());
    }

    public RuleTemplateMaterializationResponse {
        createdRules = List.copyOf(createdRules);
        skippedTemplates = List.copyOf(skippedTemplates);
    }
}
