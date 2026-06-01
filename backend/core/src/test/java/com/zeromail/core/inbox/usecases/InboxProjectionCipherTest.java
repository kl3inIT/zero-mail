package com.zeromail.core.inbox.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.inbox.domain.EncryptedField;
import com.zeromail.core.shared.crypto.CryptoProperties;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the per-field cipher. AAD coverage is the important property: a ciphertext from
 * one (tenant, message, field) tuple must NOT decrypt under any other tuple, even with the same
 * KEK. AES-GCM authenticates the AAD as part of the tag.
 */
class InboxProjectionCipherTest {

    private static final String ZERO_KEY_BASE64 = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    private final InboxProjectionCipher cipher =
            new InboxProjectionCipher(new CryptoProperties(ZERO_KEY_BASE64, ZERO_KEY_BASE64, ZERO_KEY_BASE64));

    private static final UUID TENANT_A = UUID.fromString("00000000-0000-0000-0000-000000000a01");
    private static final UUID TENANT_B = UUID.fromString("00000000-0000-0000-0000-000000000b02");
    private static final String MESSAGE_X = "190000000000aaaa";
    private static final String MESSAGE_Y = "190000000000bbbb";

    @Test
    void encrypt_then_decrypt_returns_original_plaintext() {
        String plaintext = "Hello inbox projection!";
        byte[] envelope = cipher.encrypt(plaintext, TENANT_A, MESSAGE_X, EncryptedField.SUBJECT);

        assertThat(envelope).isNotEmpty();
        assertThat(cipher.decrypt(envelope, TENANT_A, MESSAGE_X, EncryptedField.SUBJECT))
                .isEqualTo(plaintext);
    }

    @Test
    void encrypt_null_returns_null_so_callers_can_pass_through_nullable_columns() {
        assertThat(cipher.encrypt(null, TENANT_A, MESSAGE_X, EncryptedField.SNIPPET)).isNull();
        assertThat(cipher.decrypt(null, TENANT_A, MESSAGE_X, EncryptedField.SNIPPET)).isNull();
    }

    @Test
    void decrypt_fails_when_tenant_id_in_AAD_differs() {
        byte[] envelope = cipher.encrypt("secret", TENANT_A, MESSAGE_X, EncryptedField.SUBJECT);

        assertThatThrownBy(
                        () -> cipher.decrypt(envelope, TENANT_B, MESSAGE_X, EncryptedField.SUBJECT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_fails_when_gmail_message_id_in_AAD_differs() {
        byte[] envelope = cipher.encrypt("secret", TENANT_A, MESSAGE_X, EncryptedField.SUBJECT);

        assertThatThrownBy(
                        () -> cipher.decrypt(envelope, TENANT_A, MESSAGE_Y, EncryptedField.SUBJECT))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_fails_when_field_name_in_AAD_differs() {
        byte[] envelope = cipher.encrypt("secret", TENANT_A, MESSAGE_X, EncryptedField.SUBJECT);

        assertThatThrownBy(
                        () -> cipher.decrypt(envelope, TENANT_A, MESSAGE_X, EncryptedField.SNIPPET))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hash_is_deterministic_for_same_email_after_case_and_whitespace_normalization() {
        byte[] hashLower = cipher.hashSenderEmail("alice@example.com");
        byte[] hashUpper = cipher.hashSenderEmail("ALICE@EXAMPLE.COM");
        byte[] hashSpaced = cipher.hashSenderEmail("  alice@example.com  ");

        assertThat(hashLower).isEqualTo(hashUpper).isEqualTo(hashSpaced);
        assertThat(hashLower).hasSize(32); // HMAC-SHA256 digest length
    }

    @Test
    void hash_differs_for_distinct_senders() {
        assertThat(cipher.hashSenderEmail("alice@example.com"))
                .isNotEqualTo(cipher.hashSenderEmail("bob@example.com"));
    }

    @Test
    void constructor_rejects_non_32_byte_field_key() {
        CryptoProperties shortKey =
                new CryptoProperties(ZERO_KEY_BASE64, "QUJD", ZERO_KEY_BASE64); // "ABC" — 3 bytes

        assertThatThrownBy(() -> new InboxProjectionCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inbox-projection-key-base64 must decode to exactly 32 bytes");
    }

    @Test
    void constructor_rejects_non_32_byte_sender_hash_key() {
        CryptoProperties shortKey =
                new CryptoProperties(ZERO_KEY_BASE64, ZERO_KEY_BASE64, "QUJD");

        assertThatThrownBy(() -> new InboxProjectionCipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "inbox-projection-sender-hash-key-base64 must decode to exactly 32 bytes");
    }
}
