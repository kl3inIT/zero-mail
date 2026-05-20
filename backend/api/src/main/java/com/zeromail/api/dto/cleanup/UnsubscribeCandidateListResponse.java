package com.zeromail.api.dto.cleanup;

import com.zeromail.core.cleanup.projection.UnsubscribeCandidateProjection;
import java.util.List;

/**
 * Wrapper response for {@code GET /api/unsubscribe/candidates} (UNS-01).
 *
 * <p>Defensive {@link List#copyOf(java.util.Collection)} in the compact constructor — callers
 * cannot mutate the list after construction.
 */
public record UnsubscribeCandidateListResponse(List<UnsubscribeCandidateResponse> items) {

    public UnsubscribeCandidateListResponse {
        items = List.copyOf(items);
    }

    public static UnsubscribeCandidateListResponse from(
            List<UnsubscribeCandidateProjection> projections) {
        return new UnsubscribeCandidateListResponse(
                projections.stream().map(UnsubscribeCandidateResponse::from).toList());
    }
}
