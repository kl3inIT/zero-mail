package com.zeromail.core.llm.usecases;

import java.util.List;
import java.util.Objects;

/**
 * Vendor-neutral chat request crossing the LlmModelClient seam.
 *
 * @param systemPrompt fixed system prompt
 * @param userMessage sanitized user content
 * @param tools project-local tool descriptors
 * @param model model id pinned per call site
 * @param temperature deterministic by default
 * @param maxTokens explicit completion cap when the call site requires one
 * @param toolChoiceRequired forces a tool call
 */
public record LlmChatRequest(
        String systemPrompt,
        String userMessage,
        List<LlmTool> tools,
        String model,
        double temperature,
        Integer maxTokens,
        boolean toolChoiceRequired) {

    public LlmChatRequest(
            String systemPrompt,
            String userMessage,
            List<LlmTool> tools,
            String model,
            double temperature,
            boolean toolChoiceRequired) {
        this(systemPrompt, userMessage, tools, model, temperature, null, toolChoiceRequired);
    }

    public LlmChatRequest {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userMessage, "userMessage");
        Objects.requireNonNull(model, "model");
        if (maxTokens != null && maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive when provided");
        }
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
