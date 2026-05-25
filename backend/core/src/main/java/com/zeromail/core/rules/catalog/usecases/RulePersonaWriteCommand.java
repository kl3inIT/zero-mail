package com.zeromail.core.rules.catalog.usecases;

public record RulePersonaWriteCommand(
        String personaKey,
        String displayNameEn,
        String displayNameVi,
        String icon,
        int displayOrder,
        boolean enabled) {

    public RulePersonaWriteCommand {
        personaKey = RuleCatalogCommandText.requireText(personaKey, "personaKey");
        displayNameEn = RuleCatalogCommandText.requireText(displayNameEn, "displayNameEn");
        displayNameVi = RuleCatalogCommandText.requireText(displayNameVi, "displayNameVi");
        icon = RuleCatalogCommandText.optionalText(icon);
        RuleCatalogCommandText.requireNonNegativeDisplayOrder(displayOrder, "displayOrder");
    }
}
