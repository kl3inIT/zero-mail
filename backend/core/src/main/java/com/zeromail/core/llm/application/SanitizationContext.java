package com.zeromail.core.llm.application;

import java.util.Map;
import java.util.Objects;

public record SanitizationContext(
        String content, int tokenCount, boolean truncated, Map<String, Object> stepMetadata) {

    public SanitizationContext {
        Objects.requireNonNull(content, "content");
        stepMetadata = stepMetadata == null ? Map.of() : Map.copyOf(stepMetadata);
    }

    public static SanitizationContext initial(String rawHtml) {
        return new SanitizationContext(rawHtml, 0, false, Map.of());
    }

    public SanitizationContext withContent(String newContent) {
        return new SanitizationContext(newContent, tokenCount, truncated, stepMetadata);
    }

    public SanitizationContext withTokenCount(int newTokenCount, boolean wasTruncated) {
        return new SanitizationContext(content, newTokenCount, wasTruncated, stepMetadata);
    }
}
