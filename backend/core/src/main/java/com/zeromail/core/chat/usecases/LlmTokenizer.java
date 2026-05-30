package com.zeromail.core.chat.usecases;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Conservative chat-token heuristic used for history truncation.
 *
 * <p>The default assumes roughly 4 Java characters per token across English and Vietnamese. This
 * intentionally over-counts some inputs so history truncates early instead of exceeding model
 * context. A model-specific tokenizer can replace this class later without changing chat memory.
 */
@Component
public class LlmTokenizer {

    private final int charsPerToken;

    @Autowired
    public LlmTokenizer(ChatProperties chatProperties) {
        this(chatProperties.tokenizer().charsPerToken());
    }

    LlmTokenizer(int charsPerToken) {
        if (charsPerToken < 1) {
            throw new IllegalArgumentException("charsPerToken must be positive");
        }
        this.charsPerToken = charsPerToken;
    }

    public int countText(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.ceilDiv(text.length(), charsPerToken);
    }

    public int countSegments(String... textSegments) {
        int tokenCount = 0;
        if (textSegments == null) {
            return tokenCount;
        }
        for (String textSegment : textSegments) {
            tokenCount += countText(textSegment);
        }
        return tokenCount;
    }
}
