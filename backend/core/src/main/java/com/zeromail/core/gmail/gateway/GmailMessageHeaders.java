package com.zeromail.core.gmail.gateway;

import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartHeader;
import java.util.Objects;
import java.util.Optional;

public final class GmailMessageHeaders {

    private GmailMessageHeaders() {}

    public static Optional<String> firstValue(MessagePart payload, String headerName) {
        if (payload == null || payload.getHeaders() == null) {
            return Optional.empty();
        }
        return payload.getHeaders().stream()
                .filter(header -> headerName.equalsIgnoreCase(header.getName()))
                .map(MessagePartHeader::getValue)
                .filter(Objects::nonNull)
                .findFirst();
    }
}
