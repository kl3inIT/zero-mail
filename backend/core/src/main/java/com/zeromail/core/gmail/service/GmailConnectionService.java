package com.zeromail.core.gmail.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.gmail.model.GmailConnectionStatus;
import com.zeromail.core.gmail.model.GmailConnectionView;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;

/**
 * Owns Gmail connection state transitions for the current tenant.
 * Controllers delegate here so the disconnect/status invariants stay in one place.
 */
@Service
public class GmailConnectionService {

    private final GmailConnectionRepository connections;

    public GmailConnectionService(GmailConnectionRepository connections) {
        this.connections = connections;
    }

    /**
     * Read-side projection of the current tenant's Gmail connection. Returns a
     * {@link GmailConnectionView#notConnected()} sentinel when the tenant never connected
     * (or completely deleted state) so controllers do not have to handle Optional<Entity>.
     */
    @Transactional(readOnly = true)
    public GmailConnectionView currentStatus(UUID tenantId) {
        return connections.findByTenantId(tenantId)
                .map(c -> new GmailConnectionView(c.getStatus().name(), c.getGoogleEmail()))
                .orElseGet(GmailConnectionView::notConnected);
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

    /**
     * Deletes the current tenant's Gmail connection row.
     * Single-domain delete only — orchestration of the cross-domain cascade lives in
     * {@code AccountDeletionController} per CL-2 + D-D1.
     */
    @Transactional
    public void deleteForCurrentTenant(UUID tenantId) {
        connections.findByTenantId(tenantId).ifPresent(connections::delete);
    }
}
