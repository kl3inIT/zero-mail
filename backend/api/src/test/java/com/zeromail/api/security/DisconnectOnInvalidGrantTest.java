package com.zeromail.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.zeromail.api.security.events.OAuth2TokenRefreshFailed;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.tenant.TenantContext;
import com.zeromail.core.tenant.persistence.TenantEntity;
import com.zeromail.core.tenant.persistence.TenantRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

class DisconnectOnInvalidGrantTest extends ApiPostgresTestBase {

    @Autowired TenantRepository tenantRepository;
    @Autowired GmailConnectionRepository gmailConnectionRepository;
    @Autowired ApplicationEventPublisher eventPublisher;

    @Test
    void invalid_grant_disconnects_specific_mailbox_only() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID primaryGmailConnectionId = UUID.randomUUID();
        UUID failingGmailConnectionId = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, "invalid-grant-specific"));
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            saveConnection(
                                    tenantId,
                                    primaryGmailConnectionId,
                                    "primary@example.test",
                                    true);
                            saveConnection(
                                    tenantId,
                                    failingGmailConnectionId,
                                    "failing@example.test",
                                    false);
                        });

        eventPublisher.publishEvent(
                new OAuth2TokenRefreshFailed(
                        tenantId.toString(),
                        failingGmailConnectionId.toString(),
                        "invalid_grant",
                        Instant.now()));

        GmailConnectionEntity primaryConnection =
                findConnection(tenantId, primaryGmailConnectionId);
        GmailConnectionEntity failingConnection =
                findConnection(tenantId, failingGmailConnectionId);
        assertThat(primaryConnection.getStatus()).isEqualTo(GmailConnectionStatus.CONNECTED);
        assertThat(primaryConnection.getDisconnectedAt()).isNull();
        assertThat(failingConnection.getStatus()).isEqualTo(GmailConnectionStatus.DISCONNECTED);
        assertThat(failingConnection.getDisconnectedAt()).isNotNull();
    }

    @Test
    void invalid_grant_without_mailbox_id_does_not_disconnect_any_mailbox() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID gmailConnectionId = UUID.randomUUID();
        tenantRepository.save(new TenantEntity(tenantId, "invalid-grant-legacy"));
        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () ->
                                saveConnection(
                                        tenantId,
                                        gmailConnectionId,
                                        "legacy-primary@example.test",
                                        true));

        eventPublisher.publishEvent(
                new OAuth2TokenRefreshFailed(tenantId.toString(), "invalid_grant", Instant.now()));

        GmailConnectionEntity reloadedConnection = findConnection(tenantId, gmailConnectionId);
        assertThat(reloadedConnection.getStatus()).isEqualTo(GmailConnectionStatus.CONNECTED);
        assertThat(reloadedConnection.getDisconnectedAt()).isNull();
    }

    private void saveConnection(
            UUID tenantId, UUID gmailConnectionId, String googleEmail, boolean primary) {
        GmailConnectionEntity gmailConnection =
                new GmailConnectionEntity(
                        gmailConnectionId, tenantId, googleEmail, GmailConnectionStatus.CONNECTED);
        gmailConnection.setPrimary(primary);
        gmailConnection.setConnectedAt(Instant.now());
        gmailConnection.setRefreshTokenEncrypted(new byte[] {1, 2, 3});
        gmailConnectionRepository.save(gmailConnection);
    }

    private GmailConnectionEntity findConnection(UUID tenantId, UUID gmailConnectionId)
            throws Exception {
        return ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(
                        () ->
                                gmailConnectionRepository
                                        .findByIdAndTenantId(gmailConnectionId, tenantId)
                                        .orElseThrow());
    }
}
