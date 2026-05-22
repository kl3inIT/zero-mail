package com.zeromail.core.rules.catalog.projection;

public record RuleActionDescriptorView(
        String actionKey,
        String label,
        String description,
        String riskLevel,
        String availabilityStatus,
        int displayOrder) {}
