package com.zeromail.core.gmail.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.google.api.services.gmail.Gmail;
import com.zeromail.core.gmail.config.GmailProperties;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.mailbox.MailboxRef;
import com.zeromail.core.shared.privacy.Sensitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class GmailApiClientFactoryMailboxCacheTest {

    private static final byte[] FIXED_AES_KEY = new byte[32];

    @Test
    void buildClientForMailbox_uses_gmailConnectionId_cache_key_not_tenantId() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID firstGmailConnectionId = UUID.randomUUID();
        UUID secondGmailConnectionId = UUID.randomUUID();
        RefreshTokenCipher refreshTokenCipher = refreshTokenCipher();
        GmailConnectionEntity firstConnection =
                connection(
                        firstGmailConnectionId,
                        tenantId,
                        "first@example.test",
                        "refresh-token-a",
                        refreshTokenCipher);
        GmailConnectionEntity secondConnection =
                connection(
                        secondGmailConnectionId,
                        tenantId,
                        "second@example.test",
                        "refresh-token-b",
                        refreshTokenCipher);
        GmailConnectionRepository gmailConnectionRepository = mock(GmailConnectionRepository.class);
        given(gmailConnectionRepository.findByIdAndTenantId(firstGmailConnectionId, tenantId))
                .willReturn(Optional.of(firstConnection));
        given(gmailConnectionRepository.findByIdAndTenantId(secondGmailConnectionId, tenantId))
                .willReturn(Optional.of(secondConnection));

        RecordingGmailApiClientFactory gmailApiClientFactory =
                new RecordingGmailApiClientFactory(gmailConnectionRepository, refreshTokenCipher);
        gmailApiClientFactory.enqueueAccessToken("access-token-a");
        gmailApiClientFactory.enqueueAccessToken("access-token-b");

        gmailApiClientFactory.buildClientForMailbox(
                new MailboxRef(tenantId, firstGmailConnectionId));
        gmailApiClientFactory.buildClientForMailbox(
                new MailboxRef(tenantId, secondGmailConnectionId));

        assertThat(gmailApiClientFactory.refreshedRefreshTokens())
                .as("Mailbox B must be a cache miss even though it shares tenantId with mailbox A")
                .containsExactly("refresh-token-a", "refresh-token-b");
        assertThat(gmailApiClientFactory.usedAccessTokens())
                .as("Mailbox B must never receive mailbox A's cached access token")
                .containsExactly("access-token-a", "access-token-b");
    }

    private static GmailConnectionEntity connection(
            UUID gmailConnectionId,
            UUID tenantId,
            String googleEmail,
            String refreshToken,
            RefreshTokenCipher refreshTokenCipher) {
        GmailConnectionEntity gmailConnection =
                new GmailConnectionEntity(
                        gmailConnectionId, tenantId, googleEmail, GmailConnectionStatus.CONNECTED);
        gmailConnection.setRefreshTokenEncrypted(
                refreshTokenCipher.encrypt(
                        refreshToken.getBytes(StandardCharsets.UTF_8), tenantId.toString()));
        return gmailConnection;
    }

    private static RefreshTokenCipher refreshTokenCipher() {
        return new RefreshTokenCipher(Map.of(1, new SecretKeySpec(FIXED_AES_KEY, "AES")), 1);
    }

    private static final class RecordingGmailApiClientFactory extends GmailApiClientFactory {

        private final Queue<String> accessTokensToReturn = new ArrayDeque<>();
        private final List<String> refreshedRefreshTokens = new ArrayList<>();
        private final List<String> usedAccessTokens = new ArrayList<>();

        private RecordingGmailApiClientFactory(
                GmailConnectionRepository gmailConnectionRepository,
                RefreshTokenCipher refreshTokenCipher) {
            super(
                    "client-id",
                    "client-secret",
                    GmailProperties.defaults(),
                    gmailConnectionRepository,
                    refreshTokenCipher);
        }

        void enqueueAccessToken(String accessToken) {
            accessTokensToReturn.add(accessToken);
        }

        List<String> refreshedRefreshTokens() {
            return refreshedRefreshTokens;
        }

        List<String> usedAccessTokens() {
            return usedAccessTokens;
        }

        @Override
        public TokenRefreshResult refreshAccessToken(String decryptedRefreshToken) {
            refreshedRefreshTokens.add(decryptedRefreshToken);
            return new TokenRefreshResult(
                    Sensitive.of(accessTokensToReturn.remove()), Instant.now().plusSeconds(3600));
        }

        @Override
        public Gmail buildGmailClient(String accessToken, Duration requestTimeout)
                throws IOException {
            usedAccessTokens.add(accessToken);
            return null;
        }
    }
}
