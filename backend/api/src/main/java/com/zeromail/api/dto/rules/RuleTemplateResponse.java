package com.zeromail.api.dto.rules;

import com.zeromail.core.rules.projection.RuleTemplateProjection;

public record RuleTemplateResponse(
        String templateKey,
        int templateVersion,
        String displayName,
        String localizedCopyKey,
        String sourceText,
        String actionSummary,
        String status,
        boolean sourcedFromOnboarding,
        boolean materialized,
        boolean customized) {

    public static RuleTemplateResponse from(RuleTemplateProjection ruleTemplateProjection) {
        return new RuleTemplateResponse(
                ruleTemplateProjection.templateKey(),
                ruleTemplateProjection.templateVersion(),
                ruleTemplateProjection.displayName(),
                ruleTemplateProjection.localizedCopyKey(),
                ruleTemplateProjection.sourceText(),
                ruleTemplateProjection.actionSummary(),
                ruleTemplateProjection.status().id(),
                ruleTemplateProjection.sourcedFromOnboarding(),
                ruleTemplateProjection.materialized(),
                ruleTemplateProjection.customized());
    }
}
