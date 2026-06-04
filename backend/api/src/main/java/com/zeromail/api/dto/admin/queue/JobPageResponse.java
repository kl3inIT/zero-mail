package com.zeromail.api.dto.admin.queue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.queue.projection.JobPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Paginated unified job list. Opaque offset cursor; {@code nextCursor} omitted on the last page.
 */
@Schema(requiredProperties = {"rows", "totalEstimate", "hasNextPage"})
public record JobPageResponse(
        List<JobRowResponse> rows,
        @JsonInclude(JsonInclude.Include.NON_NULL) String nextCursor,
        int totalEstimate,
        boolean hasNextPage) {

    public JobPageResponse {
        rows = List.copyOf(rows);
    }

    public static JobPageResponse from(JobPage page) {
        return new JobPageResponse(
                page.rows().stream().map(JobRowResponse::from).toList(),
                page.nextCursor(),
                page.totalEstimate(),
                page.hasNextPage());
    }
}
