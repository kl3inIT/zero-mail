package com.zeromail.core.waitlist.projection;

import com.zeromail.core.waitlist.domain.WaitlistStatus;
import com.zeromail.core.waitlist.persistence.WaitlistEmailEntity;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-side snapshot of a {@code waitlist_email} row exposed to admin endpoints. Decouples the API
 * layer from the JPA-managed entity type so controllers don't accidentally hold a managed entity
 * past the transaction boundary.
 */
public record WaitlistEntryProjection(
        UUID id,
        String email,
        WaitlistStatus status,
        String source,
        Instant createdAt,
        Instant approvedAt,
        UUID approvedByAdminId,
        Instant inviteSentAt,
        Instant inviteNextAttemptAt,
        String inviteFailureReason) {

    public static WaitlistEntryProjection from(WaitlistEmailEntity entity) {
        return new WaitlistEntryProjection(
                entity.getId(),
                entity.getEmail(),
                entity.getStatus(),
                entity.getSource(),
                entity.getCreatedAt(),
                entity.getApprovedAt(),
                entity.getApprovedByAdminId(),
                entity.getInviteSentAt(),
                entity.getInviteNextAttemptAt(),
                entity.getInviteFailureReason());
    }
}
