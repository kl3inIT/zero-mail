package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RuleTemplateMaterializationResult;

public record SkippedTemplateResponse(String templateKey, String reason) {

    static SkippedTemplateResponse from(
            RuleTemplateMaterializationResult.SkippedTemplate skippedTemplate) {
        return new SkippedTemplateResponse(
                skippedTemplate.templateKey(), skippedTemplate.reason().name());
    }
}
