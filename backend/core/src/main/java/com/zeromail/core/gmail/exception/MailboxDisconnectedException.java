package com.zeromail.core.gmail.exception;

import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.shared.error.ErrorCodes;
import com.zeromail.core.shared.exception.BusinessException;
import com.zeromail.core.shared.exception.ErrorClass;
import java.util.UUID;

public final class MailboxDisconnectedException extends BusinessException {

    public MailboxDisconnectedException(
            UUID tenantId, UUID gmailConnectionId, GmailConnectionStatus status) {
        super(
                "Gmail mailbox is not connected for tenant "
                        + tenantId
                        + ", mailbox "
                        + gmailConnectionId
                        + ", status "
                        + status);
    }

    @Override
    public ErrorClass errorClass() {
        return ErrorClass.CONFLICT;
    }

    @Override
    public String errorCode() {
        return ErrorCodes.GMAIL_DISCONNECTED;
    }

    @Override
    public String logEvent() {
        return "gmail_mailbox_disconnected";
    }

    @Override
    public String title() {
        return "Gmail mailbox is not connected";
    }

    @Override
    public String detail() {
        return "The selected Gmail mailbox is not currently connected.";
    }
}
