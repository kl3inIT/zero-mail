package com.zeromail.core.account.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.UUID;

/**
 * Thrown when the request is authenticated and tenant-bound, but no user row exists for the current
 * tenant. This is an authentication/identity-consistency failure (the session refers to a user that
 * has been deleted or was never provisioned), not a business conflict — the HTTP layer maps it to
 * 401, not 409.
 *
 * <p>The tenant id is captured for server-side diagnostics only and MUST NOT be echoed in the
 * client-facing problem detail; clients receive a stable message key.
 */
public final class CurrentUserNotFoundException extends BusinessException {

    private final UUID tenantId;

    public CurrentUserNotFoundException(UUID tenantId) {
        super("Current user not found for tenant " + tenantId);
        this.tenantId = tenantId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.UNAUTHORIZED;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.AUTH_CURRENT_USER_NOT_FOUND;
    }

    @Override
    public String logEvent() {
        return "current_user_missing";
    }

    @Override
    public String title() {
        return "Current user is not available";
    }

    @Override
    public String detail() {
        return "The authenticated session points at a user that no longer exists.";
    }
}
