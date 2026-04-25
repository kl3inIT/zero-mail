package com.zeromail.api.security;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

import com.zeromail.api.security.events.OAuth2TokenRefreshFailed;
import com.zeromail.api.support.ApiPostgresTestBase;
import com.zeromail.core.persistence.GmailConnectionEntity;
import com.zeromail.core.persistence.GmailConnectionRepository;
import com.zeromail.core.persistence.GmailConnectionStatus;
import com.zeromail.core.persistence.TenantEntity;
import com.zeromail.core.persistence.TenantRepository;
import com.zeromail.core.tenant.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;

class DisconnectOnInvalidGrantTest extends ApiPostgresTestBase {

    @Autowired TenantRepository tenants;
    @Autowired GmailConnectionRepository conns;
    @Autowired ApplicationEventPublisher publisher;

    @Test
    void invalid_grant_flips_status_to_DISCONNECTED() {
        UUID tenantId = UUID.randomUUID();
        tenants.save(new TenantEntity(tenantId, "t"));

        ScopedValue.where(TenantContext.TENANT, tenantId.toString()).run(() -> {
            var conn = new GmailConnectionEntity(
                    UUID.randomUUID(), tenantId, "user@example.com", GmailConnectionStatus.CONNECTED);
            conn.setConnectedAt(Instant.now());
            conn.setRefreshTokenEncrypted(new byte[]{1, 2, 3});
            conns.save(conn);
        });

        publisher.publishEvent(new OAuth2TokenRefreshFailed(tenantId.toString(), "invalid_grant", Instant.now()));

        var reloaded = ScopedValue.where(TenantContext.TENANT, tenantId.toString())
                .call(() -> conns.findByTenantId(tenantId).orElseThrow());
        assertThat(reloaded.getStatus()).isEqualTo(GmailConnectionStatus.DISCONNECTED);
        assertThat(reloaded.getDisconnectedAt()).isNotNull();
    }
}
