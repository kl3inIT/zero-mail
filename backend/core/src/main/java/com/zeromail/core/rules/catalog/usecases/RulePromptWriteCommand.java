package com.zeromail.core.rules.catalog.usecases;

public record RulePromptWriteCommand(
        String exampleTextEn,
        String exampleTextVi,
        int displayOrder,
        boolean enabled,
        String sourceRef) {

    public RulePromptWriteCommand {
        exampleTextEn = RuleCatalogCommandText.requireText(exampleTextEn, "exampleTextEn");
        exampleTextVi = RuleCatalogCommandText.requireText(exampleTextVi, "exampleTextVi");
        sourceRef = RuleCatalogCommandText.requireText(sourceRef, "sourceRef");
        RuleCatalogCommandText.requireNonNegativeDisplayOrder(displayOrder, "displayOrder");
    }
}
