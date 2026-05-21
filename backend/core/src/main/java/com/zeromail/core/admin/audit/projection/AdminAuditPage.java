package com.zeromail.core.admin.audit.projection;

import java.util.List;
import java.util.Objects;

public record AdminAuditPage(List<AdminAuditRow> items, boolean hasNextPage) {

    public AdminAuditPage {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    }
}
