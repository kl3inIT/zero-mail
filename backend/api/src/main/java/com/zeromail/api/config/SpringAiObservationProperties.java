package com.zeromail.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = SpringAiObservationProperties.PREFIX)
public record SpringAiObservationProperties(
        boolean logPrompt, boolean logCompletion, boolean includeErrorLogging) {

    public static final String PREFIX = "spring.ai.chat.observations";
    public static final String LOG_PROMPT_KEY = PREFIX + ".log-prompt";
    public static final String LOG_COMPLETION_KEY = PREFIX + ".log-completion";
}
