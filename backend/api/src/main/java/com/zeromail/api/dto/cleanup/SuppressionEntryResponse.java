package com.zeromail.api.dto.cleanup;

import com.zeromail.core.cleanup.projection.SenderSuppressionProjection;
import java.time.Instant;
import java.util.UUID;

/**
 * One row in the per-tenant suppression list (UNS-02). Exactly one of {@code senderEmail} / {@code
 * senderDomain} is non-null (XOR enforced by the underlying projection). {@code reason} is the
 * {@link com.zeromail.core.cleanup.domain.SuppressionReason} id string ({@code manual} / {@code
 * replied} / {@code auto}).
 */
public record SuppressionEntryResponse(
        UUID id, String senderEmail, String senderDomain, String reason, Instant createdAt) {

    public static SuppressionEntryResponse from(SenderSuppressionProjection projection) {
        return new SuppressionEntryResponse(
                projection.id(),
                projection.senderEmail(),
                projection.senderDomain(),
                projection.reason().id(),
                projection.createdAt());
    }
}
