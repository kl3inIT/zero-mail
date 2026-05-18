package com.zeromail.core.chat.usecases;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "zero-mail.chat")
@Validated
public record ZeroMailChatProperties(@Valid TokenizerProperties tokenizer) {

    public ZeroMailChatProperties {
        tokenizer = tokenizer == null ? new TokenizerProperties(4) : tokenizer;
    }

    public record TokenizerProperties(@Min(1) @DefaultValue("4") int charsPerToken) {}
}
