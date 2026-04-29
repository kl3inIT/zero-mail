package com.zeromail.core.gmail.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.zeromail.core.gmail.model.GmailConnectionStatus;
import com.zeromail.core.gmail.model.GmailIngestionHealth;
import com.zeromail.core.gmail.model.GmailConnectionProjection;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;

/**
 * Owns Gmail connection state transitions for the current tenant.
 * Controllers delegate here so the disconnect/status invariants stay in one place.
 */
@Service
public class GmailConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GmailConnectionService.class);

    private final GmailConnectionRepository connections;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final RefreshTokenCipher refreshTokenCipher;
    private final TransactionTemplate disconnectTx;

    public GmailConnectionService(GmailConnectionRepository connections,
                                  GmailApiClientFactory gmailApiClientFactory,
                                  RefreshTokenCipher refreshTokenCipher,
                                  PlatformTransactionManager txManager) {
        this.connections = connections;
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.refreshTokenCipher = refreshTokenCipher;
        this.disconnectTx = new TransactionTemplate(txManager);
    }

    /**
     * Read-side projection of the current tenant's Gmail connection. Returns a
     * {@link GmailConnectionProjection#notConnected()} sentinel when the tenant never connected
     * (or completely deleted state) so controllers do not have to handle Optional<Entity>.
     */
    @Transactional(readOnly = true)
    public GmailConnectionProjection currentStatus(UUID tenantId) {
        return connections.findByTenantId(tenantId)
                .map(c -> new GmailConnectionProjection(c.getStatus().name(), c.getGoogleEmail()))
                .orElseGet(GmailConnectionProjection::notConnected);
    }

    /**
     * Marks the tenant's Gmail connection as disconnected. No-op when no connection exists —
     * the user may never have connected, or may have already disconnected.
     */
    public void disconnect(UUID tenantId) {
        markDisconnected(tenantId);
        tryStopWatch(tenantId);
    }

    public void markDisconnected(UUID tenantId) {
        disconnectTx.executeWithoutResult(_ -> connections.findByTenantId(tenantId).ifPresent(c -> {
            c.setStatus(GmailConnectionStatus.DISCONNECTED);
            c.setDisconnectedAt(Instant.now());
            c.setWatchExpiresAt(null);
            c.setWatchHistoryId(null);
            c.setWatchRenewedAt(null);
            c.setWatchConsecutiveFailures(0);
            c.setIngestionHealth(GmailIngestionHealth.HEALTHY);
            connections.save(c);
        }));
    }

    private void tryStopWatch(UUID tenantId) {
        try {
            GmailConnectionEntity connection = connections.findByTenantId(tenantId).orElse(null);
            if (connection == null || connection.getRefreshTokenEncrypted() == null) {
                return;
            }
            String decryptedToken = new String(
                    refreshTokenCipher.decrypt(connection.getRefreshTokenEncrypted(), tenantId.toString()),
                    StandardCharsets.UTF_8);
            GmailApiClientFactory.TokenRefreshResult tokenResult =
                    gmailApiClientFactory.refreshAccessToken(decryptedToken);
            gmailApiClientFactory.buildGmailClient(tokenResult.accessToken())
                    .users()
                    .stop("me")
                    .execute();
        } catch (Exception e) {
            log.warn("event=gmail_watch_stop_failed tenantId={}", tenantId);
        }
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

    @Transactional
    public void markHistoryLost(UUID tenantId, Long newPointer) {
        connections.findByTenantId(tenantId).ifPresent(c -> {
            c.setLastSyncedHistoryId(newPointer);
            c.setIngestionHealth(GmailIngestionHealth.HISTORY_LOST);
            connections.save(c);
        });
    }

    @Transactional
    public void markWatchUnhealthy(UUID tenantId) {
        connections.findByTenantId(tenantId).ifPresent(c -> {
            c.setIngestionHealth(GmailIngestionHealth.WATCH_UNHEALTHY);
            connections.save(c);
        });
    }

    @Transactional
    public void recordWatchSuccess(UUID tenantId, Long watchHistoryId, Instant watchExpiresAt) {
        connections.findByTenantId(tenantId).ifPresent(c -> {
            c.setWatchHistoryId(watchHistoryId);
            if (c.getLastSyncedHistoryId() == null) {
                c.setLastSyncedHistoryId(watchHistoryId);
            }
            c.setWatchExpiresAt(watchExpiresAt);
            c.setWatchRenewedAt(Instant.now());
            c.setWatchConsecutiveFailures(0);
            if (c.getIngestionHealth() == GmailIngestionHealth.WATCH_UNHEALTHY) {
                c.setIngestionHealth(GmailIngestionHealth.HEALTHY);
            }
            connections.save(c);
        });
    }

    @Transactional
    public void incrementWatchFailure(UUID tenantId) {
        connections.findByTenantId(tenantId).ifPresent(c -> {
            c.setWatchConsecutiveFailures(c.getWatchConsecutiveFailures() + 1);
            connections.save(c);
        });
    }

    @Transactional
    public void clearForReconnect(UUID tenantId) {
        connections.findByTenantId(tenantId).ifPresent(c -> {
            c.setWatchExpiresAt(null);
            c.setWatchHistoryId(null);
            c.setLastSyncedHistoryId(null);
            c.setWatchConsecutiveFailures(0);
            c.setIngestionHealth(GmailIngestionHealth.HEALTHY);
            connections.save(c);
        });
    }
}
