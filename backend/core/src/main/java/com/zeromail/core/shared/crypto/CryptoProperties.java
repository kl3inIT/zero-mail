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
 * consume it, rather than a standalone {@code crypto.config} package.
 *
 * <p>Each key is a separate data-class boundary so a single key leak does not cross-contaminate.
 * OAuth refresh-token plaintexts and Gmail inbox metadata are different data classes (different
 * exposure windows, different threat models); reusing one key would widen blast radius. The sender
 * email hash key is also separate so the keyed HMAC of a sender email cannot be recomputed from the
 * field encryption key alone.
 *
 * <p>The {@code toString()} is hand-masked so the AES-256 KEKs never leak via accidental bean
 * logging — Spring Boot does not auto-mask secrets in {@code @ConfigurationProperties}.
 */
@ConfigurationProperties(prefix = "zero-mail.crypto")
@Validated
public record CryptoProperties(
        @NotBlank String refreshTokenKeyBase64,
        @NotBlank String inboxProjectionKeyBase64,
        @NotBlank String inboxProjectionSenderHashKeyBase64) {

    @Override
    public @NonNull String toString() {
        return "CryptoProperties[refreshTokenKeyBase64=****,"
                + "inboxProjectionKeyBase64=****,inboxProjectionSenderHashKeyBase64=****]";
    }
}
