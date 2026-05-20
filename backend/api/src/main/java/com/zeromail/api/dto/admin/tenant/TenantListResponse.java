package com.zeromail.api.dto.admin.tenant;

import com.zeromail.core.admin.tenant.projection.TenantListPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"rows", "hasNextPage"})
public record TenantListResponse(
        List<TenantListRowResponse> rows, String nextCursor, boolean hasNextPage) {

    public TenantListResponse {
        rows = List.copyOf(rows);
    }

    public static TenantListResponse from(TenantListPage tenantListPage) {
        return new TenantListResponse(
                tenantListPage.rows().stream().map(TenantListRowResponse::from).toList(),
                tenantListPage.nextCursor(),
                tenantListPage.hasNextPage());
    }
}
