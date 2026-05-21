package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.usecases.RuleTemplateMaterializationResult;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"templateKey", "reason"})
public record SkippedTemplateResponse(String templateKey, String reason) {

    static SkippedTemplateResponse from(
            RuleTemplateMaterializationResult.SkippedTemplate skippedTemplate) {
        return new SkippedTemplateResponse(
                skippedTemplate.templateKey(), skippedTemplate.reason().name());
    }
}
