package com.zeromail.core.outbound.usecases;

import com.google.api.services.gmail.model.Message;
import com.zeromail.core.gmail.gateway.MailboxRef;
import java.util.Objects;
import java.util.UUID;

public record OutboundSendCommand(UUID tenantId, MailboxRef mailboxRef, Message gmailMessage) {

    public OutboundSendCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(mailboxRef, "mailboxRef must not be null");
        if (!tenantId.equals(mailboxRef.tenantId())) {
            throw new IllegalArgumentException("mailboxRef tenantId must match command tenantId");
        }
        Objects.requireNonNull(gmailMessage, "gmailMessage must not be null");
    }
}
