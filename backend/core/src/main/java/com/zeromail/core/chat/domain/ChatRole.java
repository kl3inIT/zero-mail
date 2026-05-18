package com.zeromail.core.chat.domain;

import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum ChatRole implements IdentifiedEnum {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system"),
    TOOL("tool");

    private final String id;

    ChatRole(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    public static ChatRole fromId(String id) {
        return Stream.of(values())
                .filter(chatRole -> chatRole.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown ChatRole id: " + id));
    }
}
