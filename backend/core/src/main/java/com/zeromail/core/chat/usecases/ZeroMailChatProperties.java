package com.zeromail.core.chat.usecases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "zero-mail.chat")
@Validated
public record ZeroMailChatProperties(
        @Min(1) @DefaultValue("15") int heartbeatIntervalSeconds,
        @Min(1) @DefaultValue("30") int sseTimeoutMinutes,
        @DefaultValue("openai/gpt-5.4-nano") String defaultModel,
        @Min(1) @DefaultValue("24576") int maxHistoryTokens,
        @Min(1) @DefaultValue("4000") int maxToolOutputTokens,
        @Min(1) @DefaultValue("4") int maxReadToolIterations,
        @Valid HistoryProperties history,
        @Valid TokenizerProperties tokenizer) {

    public ZeroMailChatProperties {
        defaultModel =
                defaultModel == null || defaultModel.isBlank()
                        ? "openai/gpt-5.4-nano"
                        : defaultModel;
        history = history == null ? new HistoryProperties(50) : history;
        tokenizer = tokenizer == null ? new TokenizerProperties(4) : tokenizer;
    }

    public record HistoryProperties(@Min(1) @DefaultValue("50") int pageSize) {}

    public record TokenizerProperties(@Min(1) @DefaultValue("4") int charsPerToken) {}
}
