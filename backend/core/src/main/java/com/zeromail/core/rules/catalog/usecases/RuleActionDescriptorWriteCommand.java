package com.zeromail.core.rules.catalog.usecases;

public record RuleActionDescriptorWriteCommand(
        String actionKey,
        String labelEn,
        String labelVi,
        String descriptionEn,
        String descriptionVi,
        String riskLevel,
        String availabilityStatus,
        int displayOrder,
        boolean enabled) {

    public RuleActionDescriptorWriteCommand {
        actionKey = RuleCatalogCommandText.requireText(actionKey, "actionKey");
        labelEn = RuleCatalogCommandText.requireText(labelEn, "labelEn");
        labelVi = RuleCatalogCommandText.requireText(labelVi, "labelVi");
        descriptionEn = RuleCatalogCommandText.requireText(descriptionEn, "descriptionEn");
        descriptionVi = RuleCatalogCommandText.requireText(descriptionVi, "descriptionVi");
        riskLevel = RuleCatalogCommandText.requireText(riskLevel, "riskLevel").toUpperCase();
        availabilityStatus =
                RuleCatalogCommandText.requireText(availabilityStatus, "availabilityStatus")
                        .toUpperCase();
        RuleCatalogCommandText.requireNonNegativeDisplayOrder(displayOrder, "displayOrder");
    }
}
