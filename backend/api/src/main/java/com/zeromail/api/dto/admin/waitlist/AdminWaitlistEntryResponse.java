package com.zeromail.api.dto.admin.waitlist;

import com.zeromail.core.waitlist.projection.WaitlistEntryProjection;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(requiredProperties = {"id", "email", "status", "createdAt"})
public record AdminWaitlistEntryResponse(
        String id,
        String email,
        @Schema(allowableValues = {"PENDING", "APPROVED", "REJECTED", "INVITED", "INVITE_FAILED"})
                String status,
        @Schema(nullable = true) String source,
        Instant createdAt,
        @Schema(nullable = true) Instant approvedAt,
        @Schema(nullable = true) String approvedByAdminId,
        @Schema(nullable = true) Instant inviteSentAt,
        @Schema(nullable = true) Instant inviteNextAttemptAt,
        @Schema(nullable = true) String inviteFailureReason) {

    public static AdminWaitlistEntryResponse from(WaitlistEntryProjection projection) {
        return new AdminWaitlistEntryResponse(
                projection.id().toString(),
                projection.email(),
                projection.status().name(),
                projection.source(),
                projection.createdAt(),
                projection.approvedAt(),
                projection.approvedByAdminId() == null
                        ? null
                        : projection.approvedByAdminId().toString(),
                projection.inviteSentAt(),
                projection.inviteNextAttemptAt(),
                projection.inviteFailureReason());
    }
}
