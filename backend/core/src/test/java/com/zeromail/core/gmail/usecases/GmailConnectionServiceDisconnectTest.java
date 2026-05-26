package com.zeromail.core.gmail.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.gateway.GoogleOAuthRevokeClient;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.support.PostgresContainerTest;
import com.zeromail.core.tenant.TenantContext;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * CASA Tier 2 V3.3.1 / V9.2.2 / V13.1.5 — disconnect MUST revoke the stored Google OAuth refresh
 * token AND clear the persisted ciphertext from {@code gmail_connection}.
 */
class GmailConnectionServiceDisconnectTest extends PostgresContainerTest {

    @Autowired GmailConnectionService gmailConnectionService;
    @Autowired GmailConnectionRepository gmailConnectionRepository;
    @Autowired RefreshTokenCipher refreshTokenCipher;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean GoogleOAuthRevokeClient googleOAuthRevokeClient;

    @Test
    void disconnect_revokes_token_and_nulls_ciphertext_when_token_present() {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        String plaintextRefreshToken = "1//connected-tenant-refresh-fixture";
        byte[] envelope =
                refreshTokenCipher.encrypt(
                        plaintextRefreshToken.getBytes(StandardCharsets.UTF_8),
                        tenantId.toString());

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            gmailConnectionService.upsert(
                                    tenantId,
                                    "connected@example.com",
                                    "https://www.googleapis.com/auth/gmail.modify",
                                    envelope);

                            gmailConnectionService.disconnect(tenantId);

                            GmailConnectionEntity connection =
                                    gmailConnectionRepository
                                            .findByTenantId(tenantId)
                                            .orElseThrow();
                            assertThat(connection.getStatus())
                                    .isEqualTo(GmailConnectionStatus.DISCONNECTED);
                            assertThat(connection.getRefreshTokenEncrypted())
                                    .as("CASA V13.1.5: ciphertext must be cleared on disconnect")
                                    .isNull();
                            verify(googleOAuthRevokeClient, times(1)).revoke(plaintextRefreshToken);
                        });
    }

    @Test
    void disconnect_skips_revoke_when_no_token_persisted() {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            // Seed a row with a null refresh token (e.g. prior disconnect).
                            gmailConnectionService.upsert(
                                    tenantId,
                                    "tokenless@example.com",
                                    "https://www.googleapis.com/auth/gmail.modify",
                                    new byte[0]);
                            // Force the ciphertext to null directly to simulate the pre-existing
                            // disconnected state. upsert() won't accept null bytes, so we update
                            // via JDBC then verify disconnect is a no-op for the revoke client.
                            jdbcTemplate.update(
                                    "UPDATE gmail_connections SET refresh_token_encrypted = NULL"
                                            + " WHERE tenant_id = ?",
                                    tenantId);

                            gmailConnectionService.disconnect(tenantId);

                            GmailConnectionEntity connection =
                                    gmailConnectionRepository
                                            .findByTenantId(tenantId)
                                            .orElseThrow();
                            assertThat(connection.getStatus())
                                    .isEqualTo(GmailConnectionStatus.DISCONNECTED);
                            verify(googleOAuthRevokeClient, never()).revoke(any());
                        });
    }

    @Test
    void disconnect_completes_even_when_revoke_call_throws() {
        UUID tenantId = UUID.randomUUID();
        seedTenant(tenantId);
        String plaintextRefreshToken = "1//throwing-revoke-fixture";
        byte[] envelope =
                refreshTokenCipher.encrypt(
                        plaintextRefreshToken.getBytes(StandardCharsets.UTF_8),
                        tenantId.toString());

        doThrow(new RuntimeException("simulated revoke failure"))
                .when(googleOAuthRevokeClient)
                .revoke(plaintextRefreshToken);

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            gmailConnectionService.upsert(
                                    tenantId,
                                    "resilient@example.com",
                                    "https://www.googleapis.com/auth/gmail.modify",
                                    envelope);

                            assertThatCode(() -> gmailConnectionService.disconnect(tenantId))
                                    .doesNotThrowAnyException();

                            GmailConnectionEntity connection =
                                    gmailConnectionRepository
                                            .findByTenantId(tenantId)
                                            .orElseThrow();
                            assertThat(connection.getStatus())
                                    .isEqualTo(GmailConnectionStatus.DISCONNECTED);
                            assertThat(connection.getRefreshTokenEncrypted())
                                    .as(
                                            "DB-side disconnect must complete even when Google revoke throws")
                                    .isNull();
                        });
    }

    private void seedTenant(UUID tenantId) {
        jdbcTemplate.update(
                "INSERT INTO tenants(id, display_name) VALUES (?, ?)",
                tenantId,
                "test-" + tenantId);
    }
}
