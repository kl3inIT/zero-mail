package com.zeromail.core.admin.auth;

import com.zeromail.core.admin.auth.domain.AdminStatus;
import java.util.Optional;
import java.util.UUID;

public record AdminUser(UUID id, String email, AdminStatus status, Optional<String> displayName) {

    public AdminUser {
        displayName = displayName == null ? Optional.empty() : displayName;
    }
}
