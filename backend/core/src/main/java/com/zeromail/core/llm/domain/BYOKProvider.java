package com.zeromail.core.llm.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum BYOKProvider implements IdentifiedEnum {
    ANTHROPIC("anthropic"),
    DEEPSEEK("deepseek"),
    GOOGLE_GENAI("google-genai"),
    OPENAI("openai");

    private static final String LEGACY_OPENAI_COMPATIBLE_ID = "openai-compatible";

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
        if (LEGACY_OPENAI_COMPATIBLE_ID.equals(id)) {
            return OPENAI;
        }
        return Stream.of(values())
                .filter(provider -> provider.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown BYOKProvider id: " + id));
    }
}
