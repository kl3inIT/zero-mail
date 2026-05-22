package com.zeromail.core.admin.queue.projection;

import java.util.List;
import java.util.Objects;

/**
 * Paginated dead-letter list. Mirrors the {@code AuditLogPage} projection idiom: opaque cursor
 * string + boolean has-next plus an estimated total surfaced to the dashboard.
 */
public record DeadLetterPage(
        List<DeadLetterRow> rows, String nextCursor, int totalEstimate, boolean hasNextPage) {

    public DeadLetterPage {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));
    }
}
