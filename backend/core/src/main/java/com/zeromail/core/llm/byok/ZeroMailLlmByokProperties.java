package com.zeromail.core.llm.byok;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("zero-mail.llm.byok")
public record ZeroMailLlmByokProperties(
        boolean allowNonVendorEndpoints,
        List<String> allowedExtraHosts,
        Duration connectTimeout,
        Duration readTimeout) {

    public ZeroMailLlmByokProperties {
        allowedExtraHosts = allowedExtraHosts == null ? List.of() : List.copyOf(allowedExtraHosts);
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(15) : readTimeout;
    }
}
