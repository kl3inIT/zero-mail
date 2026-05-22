package com.zeromail.api.dto.admin.grants;

import com.zeromail.core.admin.auth.usecases.AdminRoleGrantService.AdminRoleGrantResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * Carries the one-time enrollment URL back to the granting admin. The URL is intentionally
 * response-only and is never copied into admin audit rows.
 */
@Schema(requiredProperties = {"adminUserId", "enrollmentUrl", "expiresAt"})
public record GrantAdminResponse(UUID adminUserId, String enrollmentUrl, Instant expiresAt) {

    public static GrantAdminResponse from(AdminRoleGrantResult adminRoleGrantResult) {
        return new GrantAdminResponse(
                adminRoleGrantResult.adminUserId(),
                adminRoleGrantResult.enrollmentUrl(),
                adminRoleGrantResult.expiresAt());
    }
}
