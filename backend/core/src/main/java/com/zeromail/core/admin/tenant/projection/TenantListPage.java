package com.zeromail.core.admin.tenant.projection;

import java.util.List;

public record TenantListPage(List<TenantListRow> rows, String nextCursor, boolean hasNextPage) {

    public TenantListPage {
        rows = List.copyOf(rows);
    }
}
