package com.zeromail.core.draft.domain;

import java.util.List;
import java.util.Objects;

public record ToneContext(String descriptorBlock, List<String> styleSnippets) {

    private static final int MAX_STYLE_SNIPPETS = 3;

    public ToneContext {
        descriptorBlock = Objects.requireNonNullElse(descriptorBlock, "").trim();
        styleSnippets =
                List.copyOf(
                        Objects.requireNonNull(styleSnippets, "styleSnippets must not be null"));
        if (styleSnippets.size() > MAX_STYLE_SNIPPETS) {
            throw new IllegalArgumentException("styleSnippets cannot exceed 3 entries");
        }
        for (String styleSnippet : styleSnippets) {
            if (styleSnippet == null) {
                throw new IllegalArgumentException("styleSnippets cannot contain null entries");
            }
        }
    }

    public static ToneContext empty() {
        return new ToneContext("", List.of());
    }
}
