package com.zeromail.core.oauth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.oauth.token.OAuthTokenStore.RowDiscriminator;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.AEADBadTagException;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * Layer 1 unit test (TESTING.md §3) for {@link OAuthTokenStore}. Locks the Phase 12 CONTEXT.md D-14
 * / D-CONN-03 crypto invariants the facade must preserve:
 *
 * <ul>
 *   <li>Round-trip for {@link RowDiscriminator#GMAIL_CONNECTION} and {@link
 *       RowDiscriminator#CALENDAR_CONNECTION}.
 *   <li>Cross-tenant decryption rejects the AAD (wrapped {@link AEADBadTagException}).
 *   <li>Nonce uniqueness across 100 same-input/same-tenant encrypt calls.
 * </ul>
 *
 * <p>Builds {@link RefreshTokenCipher} with the same random-AES-256 bootstrap pattern as {@code
 * RefreshTokenCipherTest}; no Spring context required.
 */
class OAuthTokenStoreRoundTripTest {

    private static OAuthTokenStore newStore() {
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        RefreshTokenCipher cipher =
                new RefreshTokenCipher(Map.of(1, new SecretKeySpec(keyBytes, "AES")), 1);
        return new OAuthTokenStore(cipher);
    }

    @Test
    void round_trip_for_gmail_connection_discriminator() {
        OAuthTokenStore store = newStore();
        UUID tenantId = UUID.randomUUID();
        byte[] plaintext = "gmail-refresh-token".getBytes();

        byte[] envelope = store.encrypt(plaintext, tenantId, RowDiscriminator.GMAIL_CONNECTION);
        byte[] roundTripped = store.decrypt(envelope, tenantId, RowDiscriminator.GMAIL_CONNECTION);

        assertThat(roundTripped).isEqualTo(plaintext);
    }

    @Test
    void round_trip_for_calendar_connection_discriminator() {
        OAuthTokenStore store = newStore();
        UUID tenantId = UUID.randomUUID();
        byte[] plaintext = "calendar-refresh-token".getBytes();

        byte[] envelope = store.encrypt(plaintext, tenantId, RowDiscriminator.CALENDAR_CONNECTION);
        byte[] roundTripped =
                store.decrypt(envelope, tenantId, RowDiscriminator.CALENDAR_CONNECTION);

        assertThat(roundTripped).isEqualTo(plaintext);
    }

    @Test
    void cross_tenant_decrypt_rejects_gmail_envelope_via_gcm_tag_check() {
        OAuthTokenStore store = newStore();
        UUID writerTenantId = UUID.randomUUID();
        UUID attackerTenantId = UUID.randomUUID();

        byte[] envelope =
                store.encrypt(
                        "secret".getBytes(), writerTenantId, RowDiscriminator.GMAIL_CONNECTION);

        assertThatThrownBy(
                        () ->
                                store.decrypt(
                                        envelope,
                                        attackerTenantId,
                                        RowDiscriminator.GMAIL_CONNECTION))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(AEADBadTagException.class);
    }

    @Test
    void cross_tenant_decrypt_rejects_calendar_envelope_via_gcm_tag_check() {
        OAuthTokenStore store = newStore();
        UUID writerTenantId = UUID.randomUUID();
        UUID attackerTenantId = UUID.randomUUID();

        byte[] envelope =
                store.encrypt(
                        "secret".getBytes(), writerTenantId, RowDiscriminator.CALENDAR_CONNECTION);

        assertThatThrownBy(
                        () ->
                                store.decrypt(
                                        envelope,
                                        attackerTenantId,
                                        RowDiscriminator.CALENDAR_CONNECTION))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(AEADBadTagException.class);
    }

    @Test
    void repeated_encrypt_for_same_tenant_and_plaintext_produces_unique_envelopes() {
        OAuthTokenStore store = newStore();
        UUID tenantId = UUID.randomUUID();
        byte[] plaintext = "constant-input".getBytes();

        Set<String> envelopesAsHex = new HashSet<>();
        for (int iteration = 0; iteration < 100; iteration++) {
            byte[] envelope =
                    store.encrypt(plaintext, tenantId, RowDiscriminator.CALENDAR_CONNECTION);
            envelopesAsHex.add(java.util.HexFormat.of().formatHex(envelope));
        }

        assertThat(envelopesAsHex)
                .as("100 same-input/same-tenant encrypts must yield 100 distinct envelopes")
                .hasSize(100);
    }
}
