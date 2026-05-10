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

import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.domain.GmailIngestionHealth;
import com.zeromail.core.gmail.projection.GmailConnectionProjection;
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

    private final GmailConnectionRepository connectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final RefreshTokenCipher refreshTokenCipher;
    private final TransactionTemplate disconnectTransaction;

    public GmailConnectionService(GmailConnectionRepository connectionRepository,
                                  GmailApiClientFactory gmailApiClientFactory,
                                  RefreshTokenCipher refreshTokenCipher,
                                  PlatformTransactionManager transactionManager) {
        this.connectionRepository = connectionRepository;
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.refreshTokenCipher = refreshTokenCipher;
        this.disconnectTransaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Read-side projection of the current tenant's Gmail connection. Returns a
     * {@link GmailConnectionProjection#notConnected()} sentinel when the tenant never connected
     * (or completely deleted state) so controllers do not have to handle Optional<Entity>.
     */
    @Transactional(readOnly = true)
    public GmailConnectionProjection currentStatus(UUID tenantId) {
        return connectionRepository.findByTenantId(tenantId)
                .map(connection -> new GmailConnectionProjection(
                        connection.getStatus().name(),
                        connection.getIngestionHealth().name(),
                        connection.getGoogleEmail()))
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
        disconnectTransaction.executeWithoutResult(_ -> connectionRepository.findByTenantId(tenantId).ifPresent(connection -> {
            connection.setStatus(GmailConnectionStatus.DISCONNECTED);
            connection.setDisconnectedAt(Instant.now());
            connection.setWatchExpiresAt(null);
            connection.setWatchHistoryId(null);
            connection.setWatchRenewedAt(null);
            connection.setWatchConsecutiveFailures(0);
            connection.setIngestionHealth(GmailIngestionHealth.HEALTHY);
            connectionRepository.save(connection);
        }));
    }

    private void tryStopWatch(UUID tenantId) {
        try {
            GmailConnectionEntity connection = connectionRepository.findByTenantId(tenantId).orElse(null);
            if (connection == null || connection.getRefreshTokenEncrypted() == null) {
                return;
            }
            String decryptedRefreshToken = new String(
                    refreshTokenCipher.decrypt(connection.getRefreshTokenEncrypted(), tenantId.toString()),
                    StandardCharsets.UTF_8);
            GmailApiClientFactory.TokenRefreshResult tokenResult =
                    gmailApiClientFactory.refreshAccessToken(decryptedRefreshToken);
            gmailApiClientFactory.buildGmailClient(tokenResult.accessToken().value())
                    .users()
                    .stop("me")
                    .execute();
        } catch (Exception watchStopException) {
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
        connectionRepository.findByTenantId(tenantId).ifPresent(connectionRepository::delete);
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
     * sẽ capture đúng tenant tại điểm caller mở transaction (Pitfall 6 / FND-05).
     *
     * <p>Privacy: KHÔNG log {@code googleEmail}, {@code scopesGranted}, hoặc
     * {@code refreshTokenEncrypted} (T-1.4-03-token-leak / D-E1). Auditing listener
     * (Phase 1.2.1) tự động cập nhật {@code version} + {@code updated_at} qua save.
     */
    @Transactional
    public void upsert(UUID tenantId, String googleEmail, String scopesGranted, byte[] refreshTokenEncrypted) {
        GmailConnectionEntity connection = connectionRepository.findByTenantId(tenantId)
                .orElseGet(() -> new GmailConnectionEntity(
                        UUID.randomUUID(), tenantId, googleEmail, GmailConnectionStatus.CONNECTED));
        connection.setStatus(GmailConnectionStatus.CONNECTED);
        connection.setRefreshTokenEncrypted(refreshTokenEncrypted);
        connection.setScopesGranted(scopesGranted);
        connection.setConnectedAt(Instant.now());
        connection.setDisconnectedAt(null);
        connectionRepository.save(connection);
    }

    @Transactional
    public void markHistoryLost(UUID tenantId, Long newPointer) {
        connectionRepository.findByTenantId(tenantId).ifPresent(connection -> {
            connection.setLastSyncedHistoryId(newPointer);
            connection.setIngestionHealth(GmailIngestionHealth.HISTORY_LOST);
            connectionRepository.save(connection);
        });
    }

    @Transactional
    public void markWatchUnhealthy(UUID tenantId) {
        connectionRepository.findByTenantId(tenantId).ifPresent(connection -> {
            connection.setIngestionHealth(GmailIngestionHealth.WATCH_UNHEALTHY);
            connectionRepository.save(connection);
        });
    }

    @Transactional
    public void recordWatchSuccess(UUID tenantId, Long watchHistoryId, Instant watchExpiresAt) {
        connectionRepository.findByTenantId(tenantId).ifPresent(connection -> {
            connection.setWatchHistoryId(watchHistoryId);
            if (connection.getLastSyncedHistoryId() == null) {
                connection.setLastSyncedHistoryId(watchHistoryId);
            }
            connection.setWatchExpiresAt(watchExpiresAt);
            connection.setWatchRenewedAt(Instant.now());
            connection.setWatchConsecutiveFailures(0);
            if (connection.getIngestionHealth() == GmailIngestionHealth.WATCH_UNHEALTHY) {
                connection.setIngestionHealth(GmailIngestionHealth.HEALTHY);
            }
            connectionRepository.save(connection);
        });
    }

    @Transactional
    public void incrementWatchFailure(UUID tenantId) {
        connectionRepository.findByTenantId(tenantId).ifPresent(connection -> {
            connection.setWatchConsecutiveFailures(connection.getWatchConsecutiveFailures() + 1);
            connectionRepository.save(connection);
        });
    }

    @Transactional
    public void clearForReconnect(UUID tenantId) {
        connectionRepository.findByTenantId(tenantId).ifPresent(connection -> {
            connection.setWatchExpiresAt(null);
            connection.setWatchHistoryId(null);
            connection.setLastSyncedHistoryId(null);
            connection.setWatchConsecutiveFailures(0);
            connection.setIngestionHealth(GmailIngestionHealth.HEALTHY);
            connectionRepository.save(connection);
        });
    }
}
