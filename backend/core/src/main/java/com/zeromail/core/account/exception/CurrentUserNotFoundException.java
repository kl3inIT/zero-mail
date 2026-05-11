package com.zeromail.core.account.exception;

import java.util.UUID;

/**
 * Thrown when the request is authenticated and tenant-bound, but no user row exists for the current
 * tenant. This is an authentication/identity-consistency failure (the session refers to a user that
 * has been deleted or was never provisioned), not a business conflict — GlobalExceptionHandler maps
 * it to HTTP 401, not 409.
 *
 * <p>The tenant id is captured for server-side diagnostics only and MUST NOT be echoed in the
 * client-facing problem detail; clients receive a stable message key.
 */
public final class CurrentUserNotFoundException extends RuntimeException {

    private final UUID tenantId;

    public CurrentUserNotFoundException(UUID tenantId) {
        super("Current user not found for tenant " + tenantId);
        this.tenantId = tenantId;
    }

    public UUID getTenantId() {
        return tenantId;
    }
}
