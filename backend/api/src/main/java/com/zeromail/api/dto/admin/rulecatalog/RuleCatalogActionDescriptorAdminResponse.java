package com.zeromail.api.dto.admin.rulecatalog;

import com.zeromail.core.rules.catalog.projection.RuleActionDescriptorAdminView;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        requiredProperties = {
            "actionKey",
            "labelEn",
            "labelVi",
            "descriptionEn",
            "descriptionVi",
            "riskLevel",
            "availabilityStatus",
            "displayOrder",
            "enabled"
        })
public record RuleCatalogActionDescriptorAdminResponse(
        String actionKey,
        String labelEn,
        String labelVi,
        String descriptionEn,
        String descriptionVi,
        String riskLevel,
        String availabilityStatus,
        int displayOrder,
        boolean enabled) {

    public static RuleCatalogActionDescriptorAdminResponse from(
            RuleActionDescriptorAdminView ruleActionDescriptorAdminView) {
        return new RuleCatalogActionDescriptorAdminResponse(
                ruleActionDescriptorAdminView.actionKey(),
                ruleActionDescriptorAdminView.labelEn(),
                ruleActionDescriptorAdminView.labelVi(),
                ruleActionDescriptorAdminView.descriptionEn(),
                ruleActionDescriptorAdminView.descriptionVi(),
                ruleActionDescriptorAdminView.riskLevel(),
                ruleActionDescriptorAdminView.availabilityStatus(),
                ruleActionDescriptorAdminView.displayOrder(),
                ruleActionDescriptorAdminView.enabled());
    }
}
