package com.zeromail.core.llm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum KeyFormat implements IdentifiedEnum {
    OPENAI_FORMAT,
    ANTHROPIC_FORMAT,
    GOOGLE_FORMAT;

    @JsonValue
    @Override
    public String id() {
        return name();
    }

    @JsonCreator
    public static KeyFormat fromId(String id) {
        return Stream.of(values())
                .filter(keyFormat -> keyFormat.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown KeyFormat id: " + id));
    }
}
