package com.zeromail.core.oauth.token;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

/**
 * Thin facade over the existing {@link RefreshTokenCipher} AES-GCM envelope. Lets Gmail (v1.0+) and
 * Calendar (v1.4 Phase 12+) — and any future Google OAuth surface — share the same cipher without
 * duplicating crypto code.
 *
 * <p><b>Crypto invariants (unchanged from {@link RefreshTokenCipher}):</b>
 *
 * <ul>
 *   <li>AES/GCM/NoPadding, 256-bit key from versioned key map, 96-bit random nonce per encrypt.
 *   <li>{@code tenantId.toString()} bound as AAD — ciphertext written for tenant A cannot be
 *       decrypted as tenant B (row-swap rejection).
 *   <li>Envelope layout {@code [key_version:int32 | nonce:12 | ciphertext]}; nonce uniqueness from
 *       SecureRandom.
 * </ul>
 *
 * <p>The {@link RowDiscriminator} parameter is currently informational — it exists at the facade
 * boundary so future per-discriminator behavior (separate keys, audit fields) is a non-breaking
 * change to call sites. Today both discriminators delegate to the identical underlying envelope.
 *
 * <p>Per Phase 12 CONTEXT.md D-14 (Claude's Discretion): {@link RefreshTokenCipher} itself is NOT
 * modified — single source of truth for the envelope keeps key rotation surface to one class.
 */
@Component
public class OAuthTokenStore {

    /**
     * Identifies which OAuth-bearing table a ciphertext row lives in. Lets the facade boundary
     * carry intent (Gmail vs Calendar) even though the underlying cipher does not yet
     * differentiate.
     */
    public enum RowDiscriminator {
        /** Stored on {@code gmail_connections.refresh_token_encrypted}. */
        GMAIL_CONNECTION,
        /** Stored on {@code calendar_connections.refresh_token_encrypted}. */
        CALENDAR_CONNECTION
    }

    private final RefreshTokenCipher refreshTokenCipher;

    public OAuthTokenStore(RefreshTokenCipher refreshTokenCipher) {
        this.refreshTokenCipher = Objects.requireNonNull(refreshTokenCipher, "refreshTokenCipher");
    }

    /**
     * Encrypts {@code plaintext} into an AES-GCM envelope bound to {@code tenantId} as AAD. The
     * {@code discriminator} is currently informational; both values delegate to the same envelope.
     */
    public byte @NonNull [] encrypt(
            byte @NonNull [] plaintext,
            @NonNull UUID tenantId,
            @NonNull RowDiscriminator discriminator) {
        Objects.requireNonNull(plaintext, "plaintext");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(discriminator, "discriminator");
        return refreshTokenCipher.encrypt(plaintext, tenantId.toString());
    }

    /**
     * Decrypts an envelope produced by {@link #encrypt(byte[], UUID, RowDiscriminator)} for the
     * same {@code tenantId}. A different tenantId triggers the GCM tag check on the AAD and throws
     * (wrapped by {@link RefreshTokenCipher}'s {@link IllegalStateException}).
     */
    public byte @NonNull [] decrypt(
            byte @NonNull [] envelope,
            @NonNull UUID tenantId,
            @NonNull RowDiscriminator discriminator) {
        Objects.requireNonNull(envelope, "envelope");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(discriminator, "discriminator");
        return refreshTokenCipher.decrypt(envelope, tenantId.toString());
    }
}
