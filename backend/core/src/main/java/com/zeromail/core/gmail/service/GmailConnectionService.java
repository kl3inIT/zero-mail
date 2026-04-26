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

    /**
     * Idempotent upsert for the leg-2 Gmail OAuth success path (D-A4).
     *
     * <p><b>Forward declaration only — Plan 01.4-03 lands the body.</b> This signature
     * exists in Plan 01.4-02 so {@code GmailOAuthSuccessHandler} compiles cleanly while
     * Plan 03 fills in the implementation in the same wave. Calling this method before
     * Plan 03 lands will throw {@link UnsupportedOperationException}.
     *
     * <p>Contract (locked by D-A4): if a row exists for {@code tenantId}, update
     * {@code googleEmail}, {@code scopesGranted}, {@code refreshTokenEncrypted},
     * {@code status=CONNECTED}, {@code connectedAt=now}, and reset
     * {@code disconnectedAt=null}; otherwise insert a new row. Single-row-per-tenant
     * invariant preserved.
     */
    @Transactional
    public void upsert(UUID tenantId, String googleEmail, String scopesGranted, byte[] refreshTokenEncrypted) {
        throw new UnsupportedOperationException("Plan 01.4-03 lands implementation");
    }
}
