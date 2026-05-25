package com.zeromail.core.cleanup.projection;

import com.zeromail.core.cleanup.domain.SuppressionReason;
import com.zeromail.core.cleanup.persistence.SenderSuppressionEntity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Read-side projection of a single {@code sender_suppression} row. Exactly one of {@code
 * senderEmail} / {@code senderDomain} is non-null (mirrors the {@code
 * ck_sender_suppression_one_target} CHECK from changelog 043).
 *
 * <p>Created via {@link #from(SenderSuppressionEntity)} so the controller never sees the
 * Hibernate-managed entity directly.
 */
public record SenderSuppressionProjection(
        UUID id,
        String senderEmail,
        String senderDomain,
        SuppressionReason reason,
        Instant createdAt) {

    public SenderSuppressionProjection {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        boolean emailPresent = senderEmail != null && !senderEmail.isBlank();
        boolean domainPresent = senderDomain != null && !senderDomain.isBlank();
        if (emailPresent == domainPresent) {
            throw new IllegalArgumentException(
                    "exactly one of senderEmail or senderDomain must be non-null (XOR)");
        }
    }

    public static SenderSuppressionProjection from(SenderSuppressionEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        return new SenderSuppressionProjection(
                entity.getSuppressionId(),
                entity.getSenderEmail(),
                entity.getSenderDomain(),
                entity.getReason(),
                entity.getCreatedAt());
    }
}
