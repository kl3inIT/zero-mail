package com.zeromail.core.account;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.persistence.GmailConnectionRepository;
import com.zeromail.core.persistence.GmailConnectionStatus;

/**
 * Owns Gmail connection state transitions for the current tenant.
 * Controllers delegate here so the disconnect/status invariants stay in one place.
 */
@Service
public class TenantConnectionService {

    private final GmailConnectionRepository connections;

    public TenantConnectionService(GmailConnectionRepository connections) {
        this.connections = connections;
    }

    /**
     * Read-side projection of the current tenant's Gmail connection. Returns a
     * {@link TenantConnectionView#notConnected()} sentinel when the tenant never connected
     * (or completely deleted state) so controllers do not have to handle Optional<Entity>.
     */
    @Transactional(readOnly = true)
    public TenantConnectionView currentStatus(UUID tenantId) {
        return connections.findByTenantId(tenantId)
                .map(c -> new TenantConnectionView(c.getStatus().name(), c.getGoogleEmail()))
                .orElseGet(TenantConnectionView::notConnected);
    }

    /**
     * Marks the tenant's Gmail connection as disconnected. No-op when no connection exists —
     * the user may never have connected, or may have already disconnected.
     */
    @Transactional
    public void disconnect(UUID tenantId) {
        connections.findByTenantId(tenantId).ifPresent(c -> {
            c.setStatus(GmailConnectionStatus.DISCONNECTED);
            c.setDisconnectedAt(Instant.now());
            connections.save(c);
        });
    }
}
