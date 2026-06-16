package com.zeromail.core.gmail.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.UUID;

public final class DuplicateActiveMailboxException extends BusinessException {

    /**
     * Which side of the global active-email uniqueness the duplicate landed on.
     *
     * <ul>
     *   <li>{@code SAME_WORKSPACE} — the address is already a CONNECTED mailbox of the SAME tenant
     *       (re-adding your own primary).
     *   <li>{@code OTHER_WORKSPACE} — the address is CONNECTED under a DIFFERENT tenant. By design
     *       a CONNECTED Gmail maps to exactly one tenant (Pub/Sub routing), so it cannot also be
     *       added here until it is freed from its own workspace.
     * </ul>
     */
    public enum Scope {
        SAME_WORKSPACE,
        OTHER_WORKSPACE
    }

    private final transient Scope scope;

    public DuplicateActiveMailboxException(UUID tenantId, Scope scope) {
        super("Duplicate active Gmail mailbox for tenant " + tenantId);
        this.scope = scope;
    }

    public Scope scope() {
        return scope;
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.CONFLICT;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.GMAIL_MAILBOX_DUPLICATE_ACTIVE;
    }

    @Override
    public String logEvent() {
        return "gmail_mailbox_duplicate_active";
    }

    @Override
    public String title() {
        return "Gmail mailbox already connected";
    }

    @Override
    public String detail() {
        return "A Gmail mailbox with this address is already connected to this workspace.";
    }
}
