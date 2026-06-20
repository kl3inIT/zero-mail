package com.zeromail.core.admin.billing.projection;

import java.util.List;

public record AdminBillingFeaturePermissionRow(
        String featureCode,
        String displayName,
        String description,
        String category,
        int fixedCreditCost,
        String unitLabel,
        int sortOrder,
        List<AdminBillingPlanPermission> planPermissions) {}
