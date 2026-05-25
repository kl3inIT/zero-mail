package com.zeromail.core.outbound.usecases;

import com.google.api.services.gmail.model.Message;
import java.util.Objects;
import java.util.UUID;

public record OutboundSendCommand(UUID tenantId, Message gmailMessage) {

    public OutboundSendCommand {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(gmailMessage, "gmailMessage must not be null");
    }
}
