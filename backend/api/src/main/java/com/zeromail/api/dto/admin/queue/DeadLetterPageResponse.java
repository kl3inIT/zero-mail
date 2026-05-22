package com.zeromail.api.dto.admin.queue;

import com.zeromail.core.admin.queue.projection.DeadLetterPage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"rows", "hasNextPage", "totalEstimate"})
public record DeadLetterPageResponse(
        List<DeadLetterRowResponse> rows,
        String nextCursor,
        int totalEstimate,
        boolean hasNextPage) {

    public DeadLetterPageResponse {
        rows = List.copyOf(rows);
    }

    public static DeadLetterPageResponse from(DeadLetterPage page) {
        return new DeadLetterPageResponse(
                page.rows().stream().map(DeadLetterRowResponse::from).toList(),
                page.nextCursor(),
                page.totalEstimate(),
                page.hasNextPage());
    }
}
