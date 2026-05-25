package com.zeromail.core.rules.catalog.projection;

public record RuleActionDescriptorAdminView(
        String actionKey,
        String labelEn,
        String labelVi,
        String descriptionEn,
        String descriptionVi,
        String riskLevel,
        String availabilityStatus,
        int displayOrder,
        boolean enabled) {}
