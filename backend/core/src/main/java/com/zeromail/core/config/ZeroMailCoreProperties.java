package com.zeromail.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "zeromail")
@Validated
public record ZeroMailCoreProperties(
        @Valid CryptoProperties crypto) {

    public ZeroMailCoreProperties {
        crypto = crypto == null ? new CryptoProperties(null) : crypto;
    }

    public record CryptoProperties(
            @NotBlank String refreshTokenKeyBase64) {

        @Override
        public String toString() {
            return "CryptoProperties[refreshTokenKeyBase64=****]";
        }
    }
}
