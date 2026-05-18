package com.zeromail.core.chat.domain.parts;

import java.util.List;

public record ChatMessageParts(int schemaVersion, List<Part> parts) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ChatMessageParts {
        schemaVersion = CURRENT_SCHEMA_VERSION;
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    public static ChatMessageParts v1(List<Part> parts) {
        return new ChatMessageParts(CURRENT_SCHEMA_VERSION, parts);
    }
}
