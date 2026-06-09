package com.zeromail.core.gmail.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.UUID;

public final class DuplicateActiveMailboxException extends BusinessException {

    public DuplicateActiveMailboxException(UUID tenantId) {
        super("Duplicate active Gmail mailbox for tenant " + tenantId);
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
