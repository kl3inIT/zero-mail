package com.zeromail.core.gmail.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Core Gmail endpoint config bound to {@code zero-mail.gmail.*}.
 *
 * <p>Extracted from the former {@code ZeroMailCoreProperties} god-object (quick task w9t). The
 * bound keys are unchanged ({@code zero-mail.gmail.api-root-url}, {@code
 * zero-mail.gmail.oauth-token-url}); only the Java owner moved.
 *
 * <p>Distinct from {@code com.zeromail.api.config.ApiProperties.GmailProperties} (prefix {@code
 * zero-mail.api.gmail}) which carries the Pub/Sub push config — different prefix, different
 * package.
 */
@ConfigurationProperties(prefix = "zero-mail.gmail")
@Validated
public record GmailProperties(
        @DefaultValue("https://gmail.googleapis.com/") @NotBlank String apiRootUrl,
        @DefaultValue("https://oauth2.googleapis.com/token") @NotNull URI oauthTokenUrl) {

    public static GmailProperties defaults() {
        return new GmailProperties(
                "https://gmail.googleapis.com/", URI.create("https://oauth2.googleapis.com/token"));
    }
}
