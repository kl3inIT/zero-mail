package com.zeromail.core.triage.projection;

import java.util.List;
import java.util.Objects;

public record AuditLogPage(List<AuditLogRow> items, String nextCursor) {

    public AuditLogPage {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
    }
}
