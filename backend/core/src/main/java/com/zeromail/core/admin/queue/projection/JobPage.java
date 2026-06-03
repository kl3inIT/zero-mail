package com.zeromail.core.admin.queue.projection;

import java.util.List;
import java.util.Objects;

/**
 * Paginated unified job list: offset-encoded cursor string + has-next flag + an estimated total for
 * the dashboard.
 */
public record JobPage(
        List<JobRow> rows, String nextCursor, int totalEstimate, boolean hasNextPage) {

    public JobPage {
        rows = List.copyOf(Objects.requireNonNull(rows, "rows must not be null"));
    }
}
