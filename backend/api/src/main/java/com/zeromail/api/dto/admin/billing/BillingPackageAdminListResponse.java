package com.zeromail.api.dto.admin.billing;

import com.zeromail.core.admin.billing.projection.BillingPackageAdminRow;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"packages"})
public record BillingPackageAdminListResponse(List<BillingPackageAdminResponse> packages) {

    public static BillingPackageAdminListResponse from(List<BillingPackageAdminRow> rows) {
        return new BillingPackageAdminListResponse(
                rows.stream().map(BillingPackageAdminResponse::from).toList());
    }
}
