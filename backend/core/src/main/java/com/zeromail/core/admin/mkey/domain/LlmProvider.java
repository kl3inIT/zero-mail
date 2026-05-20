package com.zeromail.core.admin.mkey.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.zeromail.core.shared.lang.IdentifiedEnum;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

public enum LlmProvider implements IdentifiedEnum {
    OPENAI("https://api.openai.com/v1"),
    ANTHROPIC("https://api.anthropic.com/v1"),
    GOOGLE("https://generativelanguage.googleapis.com/v1beta"),
    DEEPSEEK("https://api.deepseek.com"),
    OPENROUTER("https://openrouter.ai/api/v1"),
    ROUTER_9R(null);

    private final String defaultBaseUrl;

    LlmProvider(String defaultBaseUrl) {
        this.defaultBaseUrl = defaultBaseUrl;
    }

    @JsonValue
    @Override
    public String id() {
        return name();
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public KeyFormat defaultKeyFormat() {
        return switch (this) {
            case OPENAI, DEEPSEEK, OPENROUTER -> KeyFormat.OPENAI_FORMAT;
            case ANTHROPIC -> KeyFormat.ANTHROPIC_FORMAT;
            case GOOGLE -> KeyFormat.GOOGLE_FORMAT;
            case ROUTER_9R -> null;
        };
    }

    public boolean acceptsKeyFormat(KeyFormat keyFormat) {
        if (this == ROUTER_9R) {
            return keyFormat == KeyFormat.OPENAI_FORMAT || keyFormat == KeyFormat.ANTHROPIC_FORMAT;
        }
        return defaultKeyFormat() == keyFormat;
    }

    @JsonCreator
    public static LlmProvider fromId(String id) {
        return Stream.of(values())
                .filter(llmProvider -> llmProvider.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown LlmProvider id: " + id));
    }
}
