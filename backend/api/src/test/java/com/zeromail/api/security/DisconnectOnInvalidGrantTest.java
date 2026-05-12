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

    @Autowired TenantRepository tenants;
    @Autowired GmailConnectionRepository conns;
    @Autowired ApplicationEventPublisher publisher;

    @Test
    void invalid_grant_flips_status_to_DISCONNECTED() {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, "t"));

        ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .run(
                        () -> {
                            var conn =
                                    new GmailConnectionEntity(
                                            UUID.randomUUID(),
                                            tenantId,
                                            "user@example.com",
                                            GmailConnectionStatus.CONNECTED);
                            conn.setConnectedAt(Instant.now());
                            conn.setRefreshTokenEncrypted(new byte[] {1, 2, 3});
                            conns.save(conn);
                        });

        publisher.publishEvent(
                new OAuth2TokenRefreshFailed(tenantId.toString(), "invalid_grant", Instant.now()));

        var reloaded =
                ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                        .call(() -> conns.findByTenantId(tenantId).orElseThrow());
        assertThat(reloaded.getStatus()).isEqualTo(GmailConnectionStatus.DISCONNECTED);
        assertThat(reloaded.getDisconnectedAt()).isNotNull();
    }
}
