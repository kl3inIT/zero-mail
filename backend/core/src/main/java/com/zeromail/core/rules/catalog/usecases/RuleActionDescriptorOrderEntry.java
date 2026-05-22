package com.zeromail.core.rules.catalog.usecases;

public record RuleActionDescriptorOrderEntry(String actionKey, int displayOrder) {

    public RuleActionDescriptorOrderEntry {
        actionKey = RuleCatalogCommandText.requireText(actionKey, "actionKey");
        RuleCatalogCommandText.requireNonNegativeDisplayOrder(displayOrder, "displayOrder");
    }
}
