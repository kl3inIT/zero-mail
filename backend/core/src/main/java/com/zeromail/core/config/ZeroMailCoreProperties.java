package com.zeromail.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;

@ConfigurationProperties(prefix = "zeromail.crypto")
@Validated
public record ZeroMailCoreProperties(
        @NotBlank String refreshTokenKeyBase64) {

    @Override
    public @NonNull String toString() {
        return "ZeroMailCoreProperties[refreshTokenKeyBase64=****]";
    }
}
