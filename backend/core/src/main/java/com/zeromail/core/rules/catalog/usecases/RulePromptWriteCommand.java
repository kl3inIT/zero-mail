package com.zeromail.core.rules.catalog.usecases;

public record RulePromptWriteCommand(
        String exampleTextEn, String exampleTextVi, int displayOrder, boolean enabled) {

    public RulePromptWriteCommand {
        exampleTextEn = RuleCatalogCommandText.requireText(exampleTextEn, "exampleTextEn");
        exampleTextVi = RuleCatalogCommandText.requireText(exampleTextVi, "exampleTextVi");
        RuleCatalogCommandText.requireNonNegativeDisplayOrder(displayOrder, "displayOrder");
    }
}
