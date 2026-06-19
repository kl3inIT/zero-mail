package com.zeromail.core.gmail.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.GoogleOAuthRevokeClient;
import com.zeromail.core.gmail.gateway.GoogleUserInfoClient;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.shared.privacy.Sensitive;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class GmailConnectionServiceProfileBackfillTest {

    @Test
    void listMailboxes_backfillsMissingProfileFromGoogleUserInfo() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID gmailConnectionId = UUID.randomUUID();
        byte[] refreshTokenEncrypted = new byte[] {1, 2, 3};
        GmailConnectionEntity gmailConnection =
                new GmailConnectionEntity(
                        gmailConnectionId,
                        tenantId,
                        "person@example.test",
                        GmailConnectionStatus.CONNECTED);
        gmailConnection.setRefreshTokenEncrypted(refreshTokenEncrypted);

        GmailConnectionRepository connectionRepository = mock(GmailConnectionRepository.class);
        GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
        GoogleUserInfoClient googleUserInfoClient = mock(GoogleUserInfoClient.class);
        RefreshTokenCipher refreshTokenCipher = mock(RefreshTokenCipher.class);
        GmailConnectionService gmailConnectionService =
                new GmailConnectionService(
                        connectionRepository,
                        gmailApiClientFactory,
                        googleUserInfoClient,
                        refreshTokenCipher,
                        mock(GoogleOAuthRevokeClient.class),
                        mock(PlatformTransactionManager.class));
        when(connectionRepository.findByTenantIdOrderByIsPrimaryDesc(tenantId))
                .thenReturn(List.of(gmailConnection));
        when(refreshTokenCipher.decrypt(refreshTokenEncrypted, tenantId.toString()))
                .thenReturn("refresh-token".getBytes(StandardCharsets.UTF_8));
        when(gmailApiClientFactory.refreshAccessToken("refresh-token"))
                .thenReturn(
                        new GmailApiClientFactory.TokenRefreshResult(
                                Sensitive.of("access-token"), Instant.now().plusSeconds(3600)));
        when(googleUserInfoClient.fetch(any()))
                .thenReturn(
                        Optional.of(
                                new GoogleUserInfoClient.GoogleUserProfile(
                                        "person@example.test",
                                        "Person Name",
                                        "https://lh3.googleusercontent.com/person")));

        var projections = gmailConnectionService.listMailboxes(tenantId);

        assertThat(projections).hasSize(1);
        assertThat(projections.getFirst().profileDisplayName()).isEqualTo("Person Name");
        assertThat(projections.getFirst().profilePictureUrl())
                .isEqualTo("https://lh3.googleusercontent.com/person");
        verify(connectionRepository).save(gmailConnection);
    }

    @Test
    void listMailboxes_doesNotBackfillWhenUserInfoEmailDoesNotMatchMailbox() throws Exception {
        UUID tenantId = UUID.randomUUID();
        byte[] refreshTokenEncrypted = new byte[] {4, 5, 6};
        GmailConnectionEntity gmailConnection =
                new GmailConnectionEntity(
                        UUID.randomUUID(),
                        tenantId,
                        "person@example.test",
                        GmailConnectionStatus.CONNECTED);
        gmailConnection.setRefreshTokenEncrypted(refreshTokenEncrypted);

        GmailConnectionRepository connectionRepository = mock(GmailConnectionRepository.class);
        GmailApiClientFactory gmailApiClientFactory = mock(GmailApiClientFactory.class);
        GoogleUserInfoClient googleUserInfoClient = mock(GoogleUserInfoClient.class);
        RefreshTokenCipher refreshTokenCipher = mock(RefreshTokenCipher.class);
        GmailConnectionService gmailConnectionService =
                new GmailConnectionService(
                        connectionRepository,
                        gmailApiClientFactory,
                        googleUserInfoClient,
                        refreshTokenCipher,
                        mock(GoogleOAuthRevokeClient.class),
                        mock(PlatformTransactionManager.class));
        when(connectionRepository.findByTenantIdOrderByIsPrimaryDesc(tenantId))
                .thenReturn(List.of(gmailConnection));
        when(refreshTokenCipher.decrypt(refreshTokenEncrypted, tenantId.toString()))
                .thenReturn("refresh-token".getBytes(StandardCharsets.UTF_8));
        when(gmailApiClientFactory.refreshAccessToken("refresh-token"))
                .thenReturn(
                        new GmailApiClientFactory.TokenRefreshResult(
                                Sensitive.of("access-token"), Instant.now().plusSeconds(3600)));
        when(googleUserInfoClient.fetch(any()))
                .thenReturn(
                        Optional.of(
                                new GoogleUserInfoClient.GoogleUserProfile(
                                        "other@example.test",
                                        "Wrong Person",
                                        "https://lh3.googleusercontent.com/wrong")));

        var projections = gmailConnectionService.listMailboxes(tenantId);

        assertThat(projections.getFirst().profileDisplayName()).isNull();
        assertThat(projections.getFirst().profilePictureUrl()).isNull();
    }
}
