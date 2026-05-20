package com.zeromail.api.dto.admin.me;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zeromail.core.admin.auth.AdminUser;
import com.zeromail.core.admin.auth.domain.AdminStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(
        name = "AdminMeResponse",
        description = "Authenticated admin's identity, returned by GET /api/admin/me.",
        requiredProperties = {"id", "email", "status", "role"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdminMeResponse(
        UUID id,
        String email,
        @Schema(nullable = true) String displayName,
        @Schema(allowableValues = {"PENDING_ENROLLMENT", "ACTIVE", "REVOKED"}) AdminStatus status,
        @Schema(description = "Static role identifier; always ADMIN for authenticated admins.")
                String role) {

    public static AdminMeResponse from(AdminUser adminUser) {
        return new AdminMeResponse(
                adminUser.id(),
                adminUser.email(),
                adminUser.displayName().orElse(null),
                adminUser.status(),
                "ADMIN");
    }
}
