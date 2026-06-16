package com.zeromail.core.gmail.support;

import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.jdbc.core.JdbcTemplate;

public final class OldSingleAccountFixture {

    public static final String KNOWN_REFRESH_TOKEN = "phase-10-known-refresh-token";
    public static final String GOOGLE_EMAIL = "old-single-account@example.test";
    public static final String SCOPES_GRANTED = "https://www.googleapis.com/auth/gmail.modify";

    private static final byte[] FIXED_AES_KEY = new byte[32];

    private OldSingleAccountFixture() {}

    public static SeededSingleAccount seed(JdbcTemplate jdbcTemplate) {
        UUID tenantId = UUID.randomUUID();
        UUID gmailConnectionId = UUID.randomUUID();
        jdbcTemplate.update(
                "insert into tenants(id, display_name) values (?, ?)",
                tenantId,
                "phase-10-old-single-account");
        return seedConnection(jdbcTemplate, tenantId, gmailConnectionId);
    }

    public static SeededSingleAccount seedConnection(
            JdbcTemplate jdbcTemplate, UUID tenantId, UUID gmailConnectionId) {
        byte[] encryptedRefreshToken = encryptKnownRefreshToken(tenantId);
        jdbcTemplate.update(
                """
                insert into gmail_connections(
                  id, tenant_id, google_email, status, refresh_token_encrypted,
                  scopes_granted, connected_at
                ) values (?, ?, ?, 'CONNECTED', ?, ?, ?)
                """,
                gmailConnectionId,
                tenantId,
                GOOGLE_EMAIL,
                encryptedRefreshToken,
                SCOPES_GRANTED,
                Timestamp.from(Instant.parse("2026-06-01T00:00:00Z")));
        return new SeededSingleAccount(
                tenantId,
                gmailConnectionId,
                KNOWN_REFRESH_TOKEN.getBytes(StandardCharsets.UTF_8),
                encryptedRefreshToken);
    }

    public static RefreshTokenCipher refreshTokenCipher() {
        return new RefreshTokenCipher(Map.of(1, new SecretKeySpec(FIXED_AES_KEY, "AES")), 1);
    }

    public static byte[] encryptKnownRefreshToken(UUID tenantId) {
        return refreshTokenCipher()
                .encrypt(KNOWN_REFRESH_TOKEN.getBytes(StandardCharsets.UTF_8), tenantId.toString());
    }

    public record SeededSingleAccount(
            UUID tenantId,
            UUID gmailConnectionId,
            byte[] knownPlaintextToken,
            byte[] encryptedRefreshToken) {}
}
