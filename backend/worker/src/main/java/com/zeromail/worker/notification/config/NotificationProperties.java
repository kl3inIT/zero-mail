package com.zeromail.worker.notification.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "zero-mail.notification")
@Validated
public record NotificationProperties(
        @Valid @NotNull EmailProperties email, @NotNull URI appBaseUrl) {

    public record EmailProperties(
            @Valid @NotNull ResendProperties resend, @NotBlank String fromAddress) {

        @Override
        public @NonNull String toString() {
            return "EmailProperties[resend=****, fromAddress=" + fromAddress + "]";
        }
    }

    public record ResendProperties(@NotBlank String apiKey) {

        @Override
        public @NonNull String toString() {
            return "ResendProperties[apiKey=****]";
        }
    }

    @Override
    public @NonNull String toString() {
        return "NotificationProperties[email=" + email + ", appBaseUrl=" + appBaseUrl + "]";
    }
}
