package com.zeromail.api.dto.rules.catalog;

import com.zeromail.core.rules.catalog.projection.RuleActionDescriptorView;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        requiredProperties = {
            "actionKey",
            "label",
            "description",
            "riskLevel",
            "availabilityStatus",
            "displayOrder"
        })
public record RuleCatalogActionDescriptorResponse(
        String actionKey,
        String label,
        String description,
        String riskLevel,
        String availabilityStatus,
        int displayOrder) {

    public static RuleCatalogActionDescriptorResponse from(
            RuleActionDescriptorView ruleActionDescriptorView) {
        return new RuleCatalogActionDescriptorResponse(
                ruleActionDescriptorView.actionKey(),
                ruleActionDescriptorView.label(),
                ruleActionDescriptorView.description(),
                ruleActionDescriptorView.riskLevel(),
                ruleActionDescriptorView.availabilityStatus(),
                ruleActionDescriptorView.displayOrder());
    }
}
