package com.zeromail.core.gmail.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.gmail.model.GmailConnectionStatus;
import com.zeromail.core.gmail.model.GmailConnectionView;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
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
     * Idempotent upsert cho leg-2 Gmail OAuth success path (D-A4). Single-row-per-tenant
     * invariant: nếu row đã tồn tại cho {@code tenantId}, UPDATE in-place; nếu chưa,
     * INSERT row mới.
     *
     * <p>Trên cả hai nhánh, các trường: {@code status=CONNECTED},
     * {@code refreshTokenEncrypted}, {@code scopesGranted}, {@code connectedAt=now},
     * và {@code disconnectedAt=null} đều được set. Việc reset {@code disconnectedAt}
     * về {@code null} là yêu cầu rõ ràng của D-A4 — re-grant sau disconnect phải xóa
     * timestamp đã disconnect cũ để view-state phản ánh đúng CONNECTED.
     *
     * <p>{@code googleEmail} chỉ được set ở constructor (path INSERT). Trên path UPDATE
     * không write lại — Phase 01.5 bundled flow đã guarantee email equality nên
     * defensive write là dư thừa (RESEARCH Q4 / D-A4
     * "defensive — should always equal stored value post-A1 check").
     *
     * <p>Caller (typically {@code GoogleOAuthSuccessHandler}) phải bind
     * {@code TenantContext.TENANT} ScopedValue TRƯỚC khi gọi method này; method dùng
     * default propagation (REQUIRED) nên join transaction của caller — JPA session
     * sẽ capture đúng tenant tại điểm caller mở tx (Pitfall 6 / FND-05).
     *
     * <p>Privacy: KHÔNG log {@code googleEmail}, {@code scopesGranted}, hoặc
     * {@code refreshTokenEncrypted} (T-1.4-03-token-leak / D-E1). Auditing listener
     * (Phase 1.2.1) tự động cập nhật {@code version} + {@code updated_at} qua save.
     */
    @Transactional
    public void upsert(UUID tenantId, String googleEmail, String scopesGranted, byte[] refreshTokenEncrypted) {
        GmailConnectionEntity row = connections.findByTenantId(tenantId)
                .orElseGet(() -> new GmailConnectionEntity(
                        UUID.randomUUID(), tenantId, googleEmail, GmailConnectionStatus.CONNECTED));
        row.setStatus(GmailConnectionStatus.CONNECTED);
        row.setRefreshTokenEncrypted(refreshTokenEncrypted);
        row.setScopesGranted(scopesGranted);
        row.setConnectedAt(Instant.now());
        row.setDisconnectedAt(null);
        connections.save(row);
    }
}
