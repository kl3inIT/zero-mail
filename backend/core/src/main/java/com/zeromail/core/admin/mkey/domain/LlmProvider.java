package com.zeromail.core.admin.mkey.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.io.Serializable;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

public final class LlmProvider implements Serializable, Comparable<LlmProvider> {

    public static final LlmProvider OPENAI = new LlmProvider("OPENAI");
    public static final LlmProvider ANTHROPIC = new LlmProvider("ANTHROPIC");
    public static final LlmProvider GOOGLE = new LlmProvider("GOOGLE");
    public static final LlmProvider DEEPSEEK = new LlmProvider("DEEPSEEK");
    public static final LlmProvider OPENROUTER = new LlmProvider("OPENROUTER");
    public static final LlmProvider ROUTER_9R = new LlmProvider("ROUTER_9R");

    private static final Pattern PROVIDER_ID_PATTERN = Pattern.compile("[A-Z0-9][A-Z0-9_:-]{1,31}");

    private static final Map<String, String> DEFAULT_BASE_URLS =
            Map.of(
                    OPENAI.id, "https://api.openai.com/v1",
                    ANTHROPIC.id, "https://api.anthropic.com/v1",
                    GOOGLE.id, "https://generativelanguage.googleapis.com/v1beta",
                    DEEPSEEK.id, "https://api.deepseek.com",
                    OPENROUTER.id, "https://openrouter.ai/api/v1");

    private final String id;

    private LlmProvider(String id) {
        this.id = id;
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static LlmProvider fromId(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        String normalizedProviderId = providerId.trim().toUpperCase(Locale.ROOT);
        if (!PROVIDER_ID_PATTERN.matcher(normalizedProviderId).matches()) {
            throw new IllegalArgumentException("Invalid LLM provider id: " + providerId);
        }
        return switch (normalizedProviderId) {
            case "OPENAI" -> OPENAI;
            case "ANTHROPIC" -> ANTHROPIC;
            case "GOOGLE" -> GOOGLE;
            case "DEEPSEEK" -> DEEPSEEK;
            case "OPENROUTER" -> OPENROUTER;
            case "ROUTER_9R" -> ROUTER_9R;
            default -> new LlmProvider(normalizedProviderId);
        };
    }

    public static LlmProvider valueOf(String providerId) {
        return fromId(providerId);
    }

    @JsonValue
    public String id() {
        return id;
    }

    public String name() {
        return id;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl(this);
    }

    public KeyFormat defaultKeyFormat() {
        return defaultKeyFormat(this);
    }

    public boolean isSpringAiBuiltIn() {
        return isSpringAiBuiltIn(this);
    }

    public boolean acceptsKeyFormat(KeyFormat keyFormat) {
        return acceptsKeyFormat(this, keyFormat);
    }

    public static String defaultBaseUrl(LlmProvider provider) {
        return DEFAULT_BASE_URLS.get(fromProvider(provider).id);
    }

    public static String defaultBaseUrl(String providerId) {
        return defaultBaseUrl(fromId(providerId));
    }

    public static KeyFormat defaultKeyFormat(LlmProvider provider) {
        return switch (fromProvider(provider).id) {
            case "OPENAI", "DEEPSEEK" -> KeyFormat.OPENAI_FORMAT;
            case "ANTHROPIC" -> KeyFormat.ANTHROPIC_FORMAT;
            case "GOOGLE" -> KeyFormat.GOOGLE_FORMAT;
            default -> null;
        };
    }

    public static KeyFormat defaultKeyFormat(String providerId) {
        return defaultKeyFormat(fromId(providerId));
    }

    public static boolean isSpringAiBuiltIn(LlmProvider provider) {
        return switch (fromProvider(provider).id) {
            case "OPENAI", "ANTHROPIC", "GOOGLE", "DEEPSEEK" -> true;
            default -> false;
        };
    }

    public static boolean isSpringAiBuiltIn(String providerId) {
        return isSpringAiBuiltIn(fromId(providerId));
    }

    public static boolean acceptsKeyFormat(LlmProvider provider, KeyFormat keyFormat) {
        if (keyFormat == null) {
            return false;
        }
        KeyFormat builtInDefault = defaultKeyFormat(provider);
        if (builtInDefault != null) {
            return builtInDefault == keyFormat;
        }
        return keyFormat == KeyFormat.OPENAI_FORMAT || keyFormat == KeyFormat.ANTHROPIC_FORMAT;
    }

    public static boolean acceptsKeyFormat(String providerId, KeyFormat keyFormat) {
        return acceptsKeyFormat(fromId(providerId), keyFormat);
    }

    @Override
    public int compareTo(LlmProvider otherProvider) {
        return id.compareTo(otherProvider.id);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LlmProvider otherProvider)) {
            return false;
        }
        return id.equals(otherProvider.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }

    private static LlmProvider fromProvider(LlmProvider provider) {
        return Objects.requireNonNull(provider, "provider");
    }
}
