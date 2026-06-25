package com.zeromail.core.admin.billing.projection;

import java.time.Instant;
import java.util.List;

public record AdminBillingPackageSnapshot(
        List<AdminBillingPlanRow> plans,
        List<AdminBillingFeaturePermissionRow> featurePermissions,
        List<AdminBillingPaymentRow> paymentHistory,
        Instant snapshotAt) {}
