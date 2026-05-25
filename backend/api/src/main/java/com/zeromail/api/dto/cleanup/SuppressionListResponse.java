package com.zeromail.api.dto.cleanup;

import com.zeromail.core.cleanup.projection.SenderSuppressionProjection;
import java.util.List;

/**
 * Wrapper response for {@code GET /api/cleanup/suppression} (UNS-02). Defensive {@link
 * List#copyOf(java.util.Collection)} in the compact constructor.
 */
public record SuppressionListResponse(List<SuppressionEntryResponse> items) {

    public SuppressionListResponse {
        items = List.copyOf(items);
    }

    public static SuppressionListResponse from(List<SenderSuppressionProjection> projections) {
        return new SuppressionListResponse(
                projections.stream().map(SuppressionEntryResponse::from).toList());
    }
}
