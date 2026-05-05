package com.zeromail.core.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.jspecify.annotations.NonNull;

@ConfigurationProperties(prefix = "zeromail")
@Validated
public record ZeroMailCoreProperties(
        @Valid @NotNull CryptoProperties crypto,
        @Valid GmailProperties gmail) {

    public ZeroMailCoreProperties {
        gmail = gmail == null ? GmailProperties.defaults() : gmail;
    }

    public record CryptoProperties(
            @NotBlank String refreshTokenKeyBase64) {
    }

    public record GmailProperties(
            @DefaultValue("https://gmail.googleapis.com/") @NotBlank String apiRootUrl,
            @DefaultValue("https://oauth2.googleapis.com/token") @NotNull URI oauthTokenUrl) {

        static GmailProperties defaults() {
            return new GmailProperties(
                    "https://gmail.googleapis.com/",
                    URI.create("https://oauth2.googleapis.com/token"));
        }
    }

    @Override
    public @NonNull String toString() {
        return "ZeroMailCoreProperties[crypto=****, gmail=" + gmail + "]";
    }
}
