package com.zeromail.api.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.api.security.events.GmailConnectionRevokedEvent;
import com.zeromail.api.security.events.OAuth2TokenRefreshFailed;
import com.zeromail.core.persistence.GmailConnectionRepository;
import com.zeromail.core.persistence.GmailConnectionStatus;
import com.zeromail.core.tenant.TenantContext;

@Service
public class GmailAccessGuard {

    private final GmailConnectionRepository conns;
    private final ApplicationEventPublisher publisher;

    public GmailAccessGuard(GmailConnectionRepository conns, ApplicationEventPublisher publisher) {
        this.conns = conns;
        this.publisher = publisher;
    }

    @EventListener
    @Transactional
    public void on(OAuth2TokenRefreshFailed e) {
        if (!"invalid_grant".equals(e.errorCode())) return;
        UUID tenant;
        try {
            tenant = UUID.fromString(e.tenantId());
        } catch (IllegalArgumentException x) {
            return;
        }
        // Bind the tenant for the duration of this DB call so Hibernate's @TenantId
        // filter sees the correct value. Events may fire from threads outside the
        // request scope (refresh background tasks, scheduled jobs).
        ScopedValue.where(TenantContext.TENANT, tenant.toString()).run(() ->
                conns.findByTenantId(tenant).ifPresent(conn -> {
                    conn.setStatus(GmailConnectionStatus.DISCONNECTED);
                    conn.setDisconnectedAt(Instant.now());
                    conns.save(conn);
                    publisher.publishEvent(new GmailConnectionRevokedEvent(tenant, Instant.now()));
                }));
    }
}
