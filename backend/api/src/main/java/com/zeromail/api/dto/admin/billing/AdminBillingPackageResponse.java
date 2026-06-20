package com.zeromail.api.dto.admin.billing;

import com.zeromail.core.admin.billing.projection.AdminBillingPackageSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(requiredProperties = {"plans", "featurePermissions", "paymentHistory", "snapshotAt"})
public record AdminBillingPackageResponse(
        List<AdminBillingPlanResponse> plans,
        List<AdminBillingFeaturePermissionResponse> featurePermissions,
        List<AdminBillingPaymentResponse> paymentHistory,
        Instant snapshotAt) {

    public AdminBillingPackageResponse {
        plans = List.copyOf(plans);
        featurePermissions = List.copyOf(featurePermissions);
        paymentHistory = List.copyOf(paymentHistory);
    }

    public static AdminBillingPackageResponse from(AdminBillingPackageSnapshot snapshot) {
        return new AdminBillingPackageResponse(
                snapshot.plans().stream().map(AdminBillingPlanResponse::from).toList(),
                snapshot.featurePermissions().stream()
                        .map(AdminBillingFeaturePermissionResponse::from)
                        .toList(),
                snapshot.paymentHistory().stream().map(AdminBillingPaymentResponse::from).toList(),
                snapshot.snapshotAt());
    }
}
