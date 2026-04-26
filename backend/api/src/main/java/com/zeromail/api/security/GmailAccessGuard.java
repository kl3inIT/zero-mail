package com.zeromail.api.security;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.zeromail.api.security.events.GmailConnectionRevokedEvent;
import com.zeromail.api.security.events.OAuth2TokenRefreshFailed;
import com.zeromail.core.gmail.model.GmailConnectionStatus;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.tenant.TenantContext;

@Service
public class GmailAccessGuard {

    private final GmailConnectionRepository conns;
    private final ApplicationEventPublisher publisher;
    private final TransactionTemplate tx;

    public GmailAccessGuard(GmailConnectionRepository conns,
                            ApplicationEventPublisher publisher,
                            PlatformTransactionManager txManager) {
        this.conns = conns;
        this.publisher = publisher;
        this.tx = new TransactionTemplate(txManager);
    }

    /**
     * Invariant: the {@code ScopedValue} must be bound BEFORE the JPA transaction opens,
     * so Hibernate's {@code CurrentTenantIdentifierResolver} captures the real tenant when
     * the session is created (rather than the bootstrap sentinel from
     * {@link com.zeromail.core.tenant.ScopedValueTenantResolver#BOOTSTRAP_TENANT}). For that
     * reason this method does not use {@code @Transactional} on itself — it would open the
     * tx before our wrap. Instead it binds the ScopedValue, then uses TransactionTemplate
     * inside the bound scope.
     */
    @EventListener
    public void on(OAuth2TokenRefreshFailed e) {
        if (!"invalid_grant".equals(e.errorCode())) return;
        UUID tenant;
        try {
            tenant = UUID.fromString(e.tenantId());
        } catch (IllegalArgumentException x) {
            return;
        }
        ScopedValue.where(TenantContext.TENANT, tenant.toString()).run(() ->
                tx.executeWithoutResult(status ->
                        conns.findByTenantId(tenant).ifPresent(conn -> {
                            conn.setStatus(GmailConnectionStatus.DISCONNECTED);
                            conn.setDisconnectedAt(Instant.now());
                            conns.save(conn);
                            publisher.publishEvent(new GmailConnectionRevokedEvent(tenant, Instant.now()));
                        })));
    }
}
