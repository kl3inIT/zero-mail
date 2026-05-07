package com.zeromail.core.llm.model;

import java.util.NoSuchElementException;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;

public enum BYOKProvider implements IdentifiedEnum {

    ANTHROPIC("anthropic"),
    OPENAI_COMPATIBLE("openai-compatible");

    private final String id;

    BYOKProvider(String id) {
        this.id = id;
    }

    @JsonValue
    @Override
    public String id() {
        return id;
    }

    @JsonCreator
    public static BYOKProvider fromId(String id) {
        return Stream.of(values())
                .filter(provider -> provider.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown BYOKProvider id: " + id));
    }
}
