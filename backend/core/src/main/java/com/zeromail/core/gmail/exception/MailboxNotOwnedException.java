package com.zeromail.core.gmail.exception;

import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.UUID;

public final class MailboxNotOwnedException extends BusinessException {

    public MailboxNotOwnedException(UUID tenantId, UUID gmailConnectionId) {
        super(
                "Gmail mailbox not found for tenant "
                        + tenantId
                        + " and mailbox "
                        + gmailConnectionId);
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.NOT_FOUND;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.GMAIL_MAILBOX_NOT_FOUND;
    }

    @Override
    public String logEvent() {
        return "gmail_mailbox_not_owned";
    }

    @Override
    public String title() {
        return "Gmail mailbox not found";
    }

    @Override
    public String detail() {
        return "The selected Gmail mailbox does not exist in this workspace.";
    }
}
