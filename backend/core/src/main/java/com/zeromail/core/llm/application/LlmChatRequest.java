package com.zeromail.core.llm.application;

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
 * @param toolChoiceRequired forces a tool call
 */
public record LlmChatRequest(
        String systemPrompt,
        String userMessage,
        List<LlmTool> tools,
        String model,
        double temperature,
        boolean toolChoiceRequired) {

    public LlmChatRequest {
        Objects.requireNonNull(systemPrompt, "systemPrompt");
        Objects.requireNonNull(userMessage, "userMessage");
        Objects.requireNonNull(model, "model");
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
