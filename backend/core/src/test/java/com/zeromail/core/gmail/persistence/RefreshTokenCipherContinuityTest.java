package com.zeromail.core.gmail.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.core.gmail.support.OldSingleAccountFixture;
import com.zeromail.core.support.PostgresContainerTest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class RefreshTokenCipherContinuityTest extends PostgresContainerTest {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void migrated_ciphertext_still_decrypts_with_tenant_aad() {
        OldSingleAccountFixture.SeededSingleAccount seededSingleAccount =
                OldSingleAccountFixture.seed(jdbcTemplate);

        byte[] encryptedRefreshToken =
                jdbcTemplate.queryForObject(
                        "select refresh_token_encrypted from gmail_connections where id = ?",
                        byte[].class,
                        seededSingleAccount.gmailConnectionId());
        byte[] decryptedRefreshToken =
                OldSingleAccountFixture.refreshTokenCipher()
                        .decrypt(encryptedRefreshToken, seededSingleAccount.tenantId().toString());

        assertThat(decryptedRefreshToken)
                .as("D-11 keeps AES-GCM AAD tenant based after mailbox migration")
                .isEqualTo(
                        OldSingleAccountFixture.KNOWN_REFRESH_TOKEN.getBytes(
                                StandardCharsets.UTF_8));
        assertThat(encryptedRefreshToken)
                .containsExactly(seededSingleAccount.encryptedRefreshToken());
    }
}
