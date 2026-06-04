package com.zeromail.core.inbox.usecases;

import com.zeromail.core.inbox.domain.EncryptedField;
import com.zeromail.core.shared.crypto.CryptoProperties;
import com.zeromail.core.shared.crypto.PlatformSecretCipher;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * Field-level cipher for the Gmail inbox projection.
 *
 * <p>Wraps {@link PlatformSecretCipher} with a per-tenant + per-message + per-field AAD so a
 * ciphertext copied from one row/field cannot be silently decrypted in another row/field. Owns its
 * own AES-256 KEK ({@code inboxProjectionKeyBase64}) and its own HMAC-SHA256 key ({@code
 * inboxProjectionSenderHashKeyBase64}), both separate from the OAuth refresh-token KEK so a single
 * leak does not cross-contaminate.
 *
 * <p>AAD format: {@code "<tenantId>:<gmailMessageId>:<fieldName>"} — UTF-8 bytes are passed to
 * {@link PlatformSecretCipher#encrypt(byte[], String)} which in turn calls {@code
 * Cipher.updateAAD}.
 *
 * <p>Sender hash: HMAC-SHA256 of the lowercase-normalized sender email under the dedicated hash
 * key. Deterministic so future lookups by sender can use the hash without exposing plaintext.
 */
@Service
public class InboxProjectionCipher {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final PlatformSecretCipher fieldCipher;
    private final SecretKeySpec senderHashKey;

    public InboxProjectionCipher(CryptoProperties cryptoProperties) {
        Objects.requireNonNull(cryptoProperties, "cryptoProperties must not be null");
        byte[] fieldKeyBytes =
                Base64.getDecoder().decode(cryptoProperties.inboxProjectionKeyBase64());
        if (fieldKeyBytes.length != 32) {
            throw new IllegalStateException(
                    "inbox-projection-key-base64 must decode to exactly 32 bytes, got "
                            + fieldKeyBytes.length);
        }
        this.fieldCipher =
                new PlatformSecretCipher(Map.of(1, new SecretKeySpec(fieldKeyBytes, "AES")), 1);

        byte[] senderHashKeyBytes =
                Base64.getDecoder().decode(cryptoProperties.inboxProjectionSenderHashKeyBase64());
        if (senderHashKeyBytes.length != 32) {
            throw new IllegalStateException(
                    "inbox-projection-sender-hash-key-base64 must decode to exactly 32 bytes, got "
                            + senderHashKeyBytes.length);
        }
        this.senderHashKey = new SecretKeySpec(senderHashKeyBytes, HMAC_ALGORITHM);
    }

    /**
     * Encrypt a UTF-8 plaintext field. Returns null when the input is null so persistence can pass
     * through nullable columns (e.g. sender display name) without an explicit null check at every
     * call site.
     */
    public byte[] encrypt(
            String plaintext, UUID tenantId, String gmailMessageId, EncryptedField field) {
        if (plaintext == null) {
            return null;
        }
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        Objects.requireNonNull(field, "field must not be null");
        return fieldCipher.encrypt(
                plaintext.getBytes(StandardCharsets.UTF_8),
                associatedData(tenantId, gmailMessageId, field));
    }

    /** Decrypt a ciphertext envelope back to UTF-8 plaintext; mirrors {@link #encrypt} nullness. */
    public String decrypt(
            byte[] envelope, UUID tenantId, String gmailMessageId, EncryptedField field) {
        if (envelope == null) {
            return null;
        }
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(gmailMessageId, "gmailMessageId must not be null");
        Objects.requireNonNull(field, "field must not be null");
        return new String(
                fieldCipher.decrypt(envelope, associatedData(tenantId, gmailMessageId, field)),
                StandardCharsets.UTF_8);
    }

    /**
     * Compute the deterministic HMAC-SHA256 of a normalized sender email. Returns the raw 32-byte
     * digest for storage in the {@code sender_email_hash} column.
     */
    public byte[] hashSenderEmail(String senderEmail) {
        Objects.requireNonNull(senderEmail, "senderEmail must not be null");
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(senderHashKey);
            return mac.doFinal(normalizeSenderEmail(senderEmail).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException unexpected) {
            throw new IllegalStateException(
                    "HmacSHA256 with a 256-bit key must be available", unexpected);
        }
    }

    private static String associatedData(
            UUID tenantId, String gmailMessageId, EncryptedField field) {
        return tenantId + ":" + gmailMessageId + ":" + field.aadName();
    }

    private static String normalizeSenderEmail(String senderEmail) {
        // Lowercase + trim so casing and surrounding whitespace from headers do not produce
        // different hashes for the same logical sender. Stop short of full RFC 5321
        // canonicalisation
        // (no plus-tag stripping, no Punycode normalization) — over-normalization is reversible
        // and risks collapsing distinct senders.
        return senderEmail.trim().toLowerCase(Locale.ROOT);
    }
}
