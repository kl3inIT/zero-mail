package com.zeromail.api.dto.admin.grants;

import com.zeromail.core.admin.auth.usecases.AdminRoleGrantService.AdminUserSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(requiredProperties = {"adminUserId", "email", "status", "hasCredential"})
public record AdminUserSummaryResponse(
        UUID adminUserId, String email, String status, Instant lastUsedAt, boolean hasCredential) {

    public static AdminUserSummaryResponse from(AdminUserSummary adminUserSummary) {
        return new AdminUserSummaryResponse(
                adminUserSummary.adminUserId(),
                adminUserSummary.email(),
                adminUserSummary.status().id(),
                adminUserSummary.lastUsedAt(),
                adminUserSummary.hasCredential());
    }
}
