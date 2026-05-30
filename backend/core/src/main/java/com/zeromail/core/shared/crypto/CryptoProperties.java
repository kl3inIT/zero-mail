package com.zeromail.core.shared.crypto;

import jakarta.validation.constraints.NotBlank;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * App-layer crypto config bound to {@code zero-mail.crypto.*}.
 *
 * <p>Extracted from the former {@code ZeroMailCoreProperties} god-object (quick task w9t) and
 * co-located with the shared crypto primitives ({@link PlatformSecretCipher}, {@link Hashing}) that
 * consume it, rather than a standalone {@code crypto.config} package. The bound key is unchanged
 * ({@code zero-mail.crypto.refresh-token-key-base64}); only the Java owner moved.
 *
 * <p>The {@code toString()} is hand-masked so the AES-256 KEK never leaks via accidental bean
 * logging — Spring Boot does not auto-mask secrets in {@code @ConfigurationProperties}.
 */
@ConfigurationProperties(prefix = "zero-mail.crypto")
@Validated
public record CryptoProperties(@NotBlank String refreshTokenKeyBase64) {

    @Override
    public @NonNull String toString() {
        return "CryptoProperties[refreshTokenKeyBase64=****]";
    }
}
