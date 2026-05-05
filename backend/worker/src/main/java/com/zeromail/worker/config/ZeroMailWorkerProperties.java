package com.zeromail.worker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@ConfigurationProperties(prefix = "zeromail")
@Validated
public record ZeroMailWorkerProperties(
        @Valid @NotNull GmailProperties gmail) {

    public record GmailProperties(
            @Valid @NotNull PubSubProperties pubsub) {
    }

    public record PubSubProperties(
            @NotBlank String topicName) {
    }
}
