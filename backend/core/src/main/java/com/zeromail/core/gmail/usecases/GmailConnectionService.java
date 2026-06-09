package com.zeromail.core.gmail.usecases;

import com.zeromail.core.gmail.domain.GmailConnectionStatus;
import com.zeromail.core.gmail.domain.GmailIngestionHealth;
import com.zeromail.core.gmail.exception.DuplicateActiveMailboxException;
import com.zeromail.core.gmail.exception.MailboxDisconnectedException;
import com.zeromail.core.gmail.exception.MailboxNotOwnedException;
import com.zeromail.core.gmail.gateway.GmailApiClientFactory;
import com.zeromail.core.gmail.gateway.GoogleOAuthRevokeClient;
import com.zeromail.core.gmail.gateway.MailboxRef;
import com.zeromail.core.gmail.persistence.GmailConnectionEntity;
import com.zeromail.core.gmail.persistence.GmailConnectionRepository;
import com.zeromail.core.gmail.persistence.crypto.RefreshTokenCipher;
import com.zeromail.core.gmail.projection.GmailConnectionProjection;
import com.zeromail.core.gmail.projection.MailboxSummaryProjection;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns Gmail connection state transitions for the current tenant. Controllers delegate here so the
 * disconnect/status invariants stay in one place.
 */
@Service
public class GmailConnectionService {

    private static final Logger log = LoggerFactory.getLogger(GmailConnectionService.class);
    private static final String DUPLICATE_ACTIVE_EMAIL_CONSTRAINT = "uq_gmail_conn_active_email";

    private final GmailConnectionRepository connectionRepository;
    private final GmailApiClientFactory gmailApiClientFactory;
    private final RefreshTokenCipher refreshTokenCipher;
    private final GoogleOAuthRevokeClient googleOAuthRevokeClient;
    private final TransactionTemplate disconnectTransaction;

    public GmailConnectionService(
            GmailConnectionRepository connectionRepository,
            GmailApiClientFactory gmailApiClientFactory,
            RefreshTokenCipher refreshTokenCipher,
            GoogleOAuthRevokeClient googleOAuthRevokeClient,
            PlatformTransactionManager transactionManager) {
        this.connectionRepository = connectionRepository;
        this.gmailApiClientFactory = gmailApiClientFactory;
        this.refreshTokenCipher = refreshTokenCipher;
        this.googleOAuthRevokeClient = googleOAuthRevokeClient;
        this.disconnectTransaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Read-side projection of the current tenant's Gmail connection. Returns a {@link
     * GmailConnectionProjection#notConnected()} sentinel when the tenant never connected (or
     * completely deleted state) so controllers do not have to handle Optional<Entity>.
     */
    @Transactional(readOnly = true)
    public GmailConnectionProjection currentStatus(UUID tenantId) {
        return connectionRepository
                .findByTenantId(tenantId)
                .map(
                        connection ->
                                new GmailConnectionProjection(
                                        connection.getStatus().name(),
                                        connection.getIngestionHealth().name(),
                                        connection.getGoogleEmail()))
                .orElseGet(GmailConnectionProjection::notConnected);
    }

    @Transactional(readOnly = true)
    public GmailConnectionEntity resolveOwnedConnectionOrThrow(
            UUID tenantId, UUID gmailConnectionId) {
        GmailConnectionEntity gmailConnection =
                connectionRepository
                        .findByIdAndTenantId(gmailConnectionId, tenantId)
                        .orElseThrow(
                                () -> {
                                    log.warn(
                                            "event=gmail_mailbox_resolve_denied tenantId={} gmailConnectionId={} reason=not_owned",
                                            tenantId,
                                            gmailConnectionId);
                                    return new MailboxNotOwnedException(
                                            tenantId, gmailConnectionId);
                                });
        if (gmailConnection.getStatus() != GmailConnectionStatus.CONNECTED) {
            log.warn(
                    "event=gmail_mailbox_resolve_denied tenantId={} gmailConnectionId={} reason=not_connected",
                    tenantId,
                    gmailConnectionId);
            throw new MailboxDisconnectedException(
                    tenantId, gmailConnectionId, gmailConnection.getStatus());
        }
        return gmailConnection;
    }

    @Transactional(readOnly = true)
    public GmailConnectionEntity resolveReconnectableConnectionOrThrow(
            UUID tenantId, UUID gmailConnectionId) {
        return connectionRepository
                .findByIdAndTenantId(gmailConnectionId, tenantId)
                .orElseThrow(
                        () -> {
                            log.warn(
                                    "event=gmail_mailbox_reconnect_resolve_denied tenantId={} gmailConnectionId={} reason=not_owned",
                                    tenantId,
                                    gmailConnectionId);
                            return new MailboxNotOwnedException(tenantId, gmailConnectionId);
                        });
    }

    public void setPrimary(UUID tenantId, UUID gmailConnectionId) {
        disconnectTransaction.executeWithoutResult(
                _ -> {
                    GmailConnectionEntity targetConnection =
                            resolveOwnedConnectionOrThrow(tenantId, gmailConnectionId);
                    // Clear every other primary atomically via a bulk UPDATE and flush it to the DB
                    // BEFORE promoting the target. This removes the dependency on Hibernate flush
                    // ordering that previously kept two is_primary=true rows from coexisting in the
                    // transaction and tripping uq_gmail_conn_primary.
                    connectionRepository.clearPrimaryForTenantExcept(tenantId, gmailConnectionId);
                    // The bulk UPDATE bypasses the persistence context, so refresh the managed
                    // target's flag from the just-resolved CONNECTED row before promoting it.
                    if (!targetConnection.isPrimary()) {
                        targetConnection.setPrimary(true);
                        connectionRepository.saveAndFlush(targetConnection);
                    }
                });
    }

    @Transactional(readOnly = true)
    public List<MailboxSummaryProjection> listMailboxes(UUID tenantId) {
        return connectionRepository.findByTenantIdOrderByIsPrimaryDesc(tenantId).stream()
                .map(MailboxSummaryProjection::from)
                .toList();
    }

    /**
     * Marks the tenant's Gmail connection as disconnected.
     *
     * <p>Ordering matters: {@link #tryStopWatch(UUID)} runs FIRST because it needs the still-
     * persisted refresh-token ciphertext to refresh an access token for Google's {@code users.stop}
     * call. Then {@link #revokeStoredRefreshToken(UUID)} POSTs to Google's revoke endpoint
     * (best-effort), and finally {@link #markDisconnected(UUID)} flips status and NULLs the
     * ciphertext on the row. CASA Tier 2 V3.3.1 / V13.1.5 require token revocation on
     * user-initiated logout/disconnect, not just clearing the local session.
     */
    public void disconnect(UUID tenantId) {
        revokeGrantForCurrentTenant(tenantId);
        markDisconnected(tenantId);
    }

    public void disconnect(MailboxRef mailboxRef) {
        GmailConnectionEntity gmailConnection =
                connectionRepository
                        .findByIdAndTenantId(mailboxRef.gmailConnectionId(), mailboxRef.tenantId())
                        .orElseThrow(
                                () ->
                                        new MailboxNotOwnedException(
                                                mailboxRef.tenantId(),
                                                mailboxRef.gmailConnectionId()));
        if (gmailConnection.getStatus() != GmailConnectionStatus.CONNECTED) {
            return;
        }
        tryStopWatch(mailboxRef);
        revokeStoredRefreshToken(mailboxRef);
        markDisconnected(mailboxRef);
    }

    /**
     * Best-effort teardown of the Google-side grant for a tenant: stop the Gmail watch ({@code
     * users.stop}) and revoke the stored refresh token. Ordering matches {@link #disconnect(UUID)}
     * — stop FIRST while the refresh-token ciphertext is still present, then revoke. Both calls
     * swallow their own exceptions so a failed external call never blocks the caller.
     *
     * <p>Exposed so the account-deletion cascade can revoke at Google BEFORE the rows are dropped —
     * otherwise deleting the connection row alone leaves the OAuth grant active in the user's
     * Google account and the Pub/Sub watch running until it expires (CASA Tier 2 V3.3.1 / V13.1.5).
     */
    public void revokeGrantForCurrentTenant(UUID tenantId) {
        tryStopWatch(tenantId);
        revokeStoredRefreshToken(tenantId);
    }

    public void markDisconnected(UUID tenantId) {
        disconnectTransaction.executeWithoutResult(
                _ ->
                        connectionRepository
                                .findByTenantId(tenantId)
                                .ifPresent(
                                        connection -> {
                                            applyDisconnectedState(connection);
                                            connection.setPrimary(false);
                                            connectionRepository.save(connection);
                                        }));
    }

    private void markDisconnected(MailboxRef mailboxRef) {
        disconnectTransaction.executeWithoutResult(
                _ ->
                        connectionRepository
                                .findByIdAndTenantId(
                                        mailboxRef.gmailConnectionId(), mailboxRef.tenantId())
                                .ifPresent(
                                        gmailConnection -> {
                                            boolean wasPrimary = gmailConnection.isPrimary();
                                            applyDisconnectedState(gmailConnection);
                                            gmailConnection.setPrimary(false);
                                            connectionRepository.save(gmailConnection);
                                            if (wasPrimary) {
                                                promoteNextPrimaryMailbox(mailboxRef);
                                            }
                                        }));
    }

    private void applyDisconnectedState(GmailConnectionEntity gmailConnection) {
        // Evict any cached access token for this connection so a still-valid (~59 min TTL) token
        // can never keep issuing Gmail API calls against a mailbox the user just disconnected.
        gmailApiClientFactory.evictAccessToken(gmailConnection.getId());
        gmailConnection.setStatus(GmailConnectionStatus.DISCONNECTED);
        gmailConnection.setDisconnectedAt(Instant.now());
        gmailConnection.setRefreshTokenEncrypted(null);
        gmailConnection.setWatchExpiresAt(null);
        gmailConnection.setWatchHistoryId(null);
        gmailConnection.setWatchRenewedAt(null);
        gmailConnection.setWatchConsecutiveFailures(0);
        gmailConnection.setIngestionHealth(GmailIngestionHealth.HEALTHY);
    }

    private void promoteNextPrimaryMailbox(MailboxRef disconnectedMailboxRef) {
        connectionRepository
                .findByTenantIdOrderByIsPrimaryDesc(disconnectedMailboxRef.tenantId())
                .stream()
                .filter(
                        gmailConnection ->
                                gmailConnection.getStatus() == GmailConnectionStatus.CONNECTED
                                        && !gmailConnection
                                                .getId()
                                                .equals(disconnectedMailboxRef.gmailConnectionId()))
                .min(
                        Comparator.comparing(
                                        GmailConnectionEntity::getConnectedAt,
                                        Comparator.nullsLast(Comparator.naturalOrder()))
                                .thenComparing(GmailConnectionEntity::getId))
                .ifPresent(
                        nextPrimaryMailbox -> {
                            nextPrimaryMailbox.setPrimary(true);
                            connectionRepository.save(nextPrimaryMailbox);
                        });
    }

    private void revokeStoredRefreshToken(UUID tenantId) {
        try {
            GmailConnectionEntity connection =
                    connectionRepository.findByTenantId(tenantId).orElse(null);
            if (connection == null || connection.getRefreshTokenEncrypted() == null) {
                return;
            }
            String decryptedRefreshToken =
                    new String(
                            refreshTokenCipher.decrypt(
                                    connection.getRefreshTokenEncrypted(), tenantId.toString()),
                            StandardCharsets.UTF_8);
            googleOAuthRevokeClient.revoke(decryptedRefreshToken);
        } catch (Exception revokeCallException) {
            // Defense-in-depth: the client itself is best-effort and never throws, but a
            // ciphertext decrypt failure (key rotation, corrupted bytes) must NOT block the
            // DB-side disconnect.
            log.warn("event=gmail_revoke_call_failed tenantId={}", tenantId);
        }
    }

    private void revokeStoredRefreshToken(MailboxRef mailboxRef) {
        try {
            GmailConnectionEntity gmailConnection =
                    connectionRepository
                            .findByIdAndTenantId(
                                    mailboxRef.gmailConnectionId(), mailboxRef.tenantId())
                            .orElse(null);
            if (gmailConnection == null || gmailConnection.getRefreshTokenEncrypted() == null) {
                return;
            }
            byte[] decryptedRefreshTokenBytes =
                    refreshTokenCipher.decrypt(
                            gmailConnection.getRefreshTokenEncrypted(),
                            mailboxRef.tenantId().toString());
            try {
                String decryptedRefreshToken =
                        new String(decryptedRefreshTokenBytes, StandardCharsets.UTF_8);
                googleOAuthRevokeClient.revoke(decryptedRefreshToken);
            } finally {
                Arrays.fill(decryptedRefreshTokenBytes, (byte) 0);
            }
        } catch (Exception revokeCallException) {
            log.warn(
                    "event=gmail_revoke_call_failed tenantId={} gmailConnectionId={}",
                    mailboxRef.tenantId(),
                    mailboxRef.gmailConnectionId());
        }
    }

    private void tryStopWatch(UUID tenantId) {
        try {
            GmailConnectionEntity connection =
                    connectionRepository.findByTenantId(tenantId).orElse(null);
            if (connection == null || connection.getRefreshTokenEncrypted() == null) {
                return;
            }
            String decryptedRefreshToken =
                    new String(
                            refreshTokenCipher.decrypt(
                                    connection.getRefreshTokenEncrypted(), tenantId.toString()),
                            StandardCharsets.UTF_8);
            GmailApiClientFactory.TokenRefreshResult tokenResult =
                    gmailApiClientFactory.refreshAccessToken(decryptedRefreshToken);
            gmailApiClientFactory
                    .buildGmailClient(tokenResult.accessToken().value())
                    .users()
                    .stop("me")
                    .execute();
        } catch (Exception watchStopException) {
            log.warn("event=gmail_watch_stop_failed tenantId={}", tenantId);
        }
    }

    private void tryStopWatch(MailboxRef mailboxRef) {
        try {
            GmailConnectionEntity gmailConnection =
                    connectionRepository
                            .findByIdAndTenantId(
                                    mailboxRef.gmailConnectionId(), mailboxRef.tenantId())
                            .orElse(null);
            if (gmailConnection == null || gmailConnection.getRefreshTokenEncrypted() == null) {
                return;
            }
            gmailApiClientFactory.buildClientForMailbox(mailboxRef).users().stop("me").execute();
        } catch (Exception watchStopException) {
            log.warn(
                    "event=gmail_watch_stop_failed tenantId={} gmailConnectionId={}",
                    mailboxRef.tenantId(),
                    mailboxRef.gmailConnectionId());
        }
    }

    /**
     * Deletes the current tenant's Gmail connection row. Single-domain delete only — orchestration
     * of the cross-domain cascade lives in {@code AccountDeletionController} per CL-2 + D-D1.
     */
    @Transactional
    public void deleteForCurrentTenant(UUID tenantId) {
        connectionRepository.findByTenantId(tenantId).ifPresent(connectionRepository::delete);
    }

    /**
     * Idempotent upsert cho leg-2 Gmail OAuth success path (D-A4). Single-row-per-tenant invariant:
     * nếu row đã tồn tại cho {@code tenantId}, UPDATE in-place; nếu chưa, INSERT row mới.
     *
     * <p>Trên cả hai nhánh, các trường: {@code status=CONNECTED}, {@code refreshTokenEncrypted},
     * {@code scopesGranted}, {@code connectedAt=now}, và {@code disconnectedAt=null} đều được set.
     * Việc reset {@code disconnectedAt} về {@code null} là yêu cầu rõ ràng của D-A4 — re-grant sau
     * disconnect phải xóa timestamp đã disconnect cũ để view-state phản ánh đúng CONNECTED.
     *
     * <p>{@code googleEmail} chỉ được set ở constructor (path INSERT). Trên path UPDATE không write
     * lại — Phase 01.5 bundled flow đã guarantee email equality nên defensive write là dư thừa
     * (RESEARCH Q4 / D-A4 "defensive — should always equal stored value post-A1 check").
     *
     * <p>Caller (typically {@code GoogleOAuthSuccessHandler}) phải bind {@code
     * TenantContext.TENANT} ScopedValue TRƯỚC khi gọi method này; method dùng default propagation
     * (REQUIRED) nên join transaction của caller — JPA session sẽ capture đúng tenant tại điểm
     * caller mở transaction (Pitfall 6 / FND-05).
     *
     * <p>Privacy: KHÔNG log {@code googleEmail}, {@code scopesGranted}, hoặc {@code
     * refreshTokenEncrypted} (T-1.4-03-token-leak / D-E1). Auditing listener (Phase 1.2.1) tự động
     * cập nhật {@code version} + {@code updated_at} qua save.
     */
    @Transactional
    public void upsert(
            UUID tenantId, String googleEmail, String scopesGranted, byte[] refreshTokenEncrypted) {
        GmailConnectionEntity connection =
                connectionRepository
                        .findByTenantId(tenantId)
                        .orElseGet(
                                () ->
                                        new GmailConnectionEntity(
                                                UUID.randomUUID(),
                                                tenantId,
                                                googleEmail,
                                                GmailConnectionStatus.CONNECTED));
        connection.setStatus(GmailConnectionStatus.CONNECTED);
        connection.setRefreshTokenEncrypted(refreshTokenEncrypted);
        connection.setScopesGranted(scopesGranted);
        connection.setConnectedAt(Instant.now());
        connection.setDisconnectedAt(null);
        connectionRepository.save(connection);
    }

    @Transactional
    public UUID addConnection(
            UUID tenantId, String googleEmail, String scopesGranted, String plaintextRefreshToken) {
        assertNoActiveDuplicate(tenantId, googleEmail);
        UUID gmailConnectionId = UUID.randomUUID();
        byte[] plaintextRefreshTokenBytes = plaintextRefreshToken.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] refreshTokenEncrypted =
                    refreshTokenCipher.encrypt(plaintextRefreshTokenBytes, tenantId.toString());
            GmailConnectionEntity gmailConnection =
                    new GmailConnectionEntity(
                            gmailConnectionId,
                            tenantId,
                            googleEmail,
                            GmailConnectionStatus.CONNECTED);
            gmailConnection.setRefreshTokenEncrypted(refreshTokenEncrypted);
            gmailConnection.setScopesGranted(scopesGranted);
            gmailConnection.setConnectedAt(Instant.now());
            gmailConnection.setPrimary(false);
            try {
                connectionRepository.saveAndFlush(gmailConnection);
            } catch (DataIntegrityViolationException dataIntegrityViolation) {
                rethrowDuplicateActiveMailboxIfMatched(tenantId, dataIntegrityViolation);
            }
            return gmailConnectionId;
        } finally {
            Arrays.fill(plaintextRefreshTokenBytes, (byte) 0);
        }
    }

    @Transactional
    public void reconnect(
            UUID tenantId,
            UUID targetMailboxId,
            String scopesGranted,
            String plaintextRefreshToken) {
        GmailConnectionEntity gmailConnection =
                resolveReconnectableConnectionOrThrow(tenantId, targetMailboxId);
        byte[] plaintextRefreshTokenBytes = plaintextRefreshToken.getBytes(StandardCharsets.UTF_8);
        try {
            byte[] refreshTokenEncrypted =
                    refreshTokenCipher.encrypt(plaintextRefreshTokenBytes, tenantId.toString());
            gmailConnection.setStatus(GmailConnectionStatus.CONNECTED);
            gmailConnection.setRefreshTokenEncrypted(refreshTokenEncrypted);
            gmailConnection.setScopesGranted(scopesGranted);
            gmailConnection.setConnectedAt(Instant.now());
            gmailConnection.setDisconnectedAt(null);
            gmailConnection.setWatchExpiresAt(null);
            gmailConnection.setWatchHistoryId(null);
            gmailConnection.setWatchRenewedAt(null);
            gmailConnection.setLastSyncedHistoryId(null);
            gmailConnection.setWatchConsecutiveFailures(0);
            gmailConnection.setIngestionHealth(GmailIngestionHealth.HEALTHY);
            try {
                connectionRepository.saveAndFlush(gmailConnection);
            } catch (DataIntegrityViolationException dataIntegrityViolation) {
                rethrowDuplicateActiveMailboxIfMatched(tenantId, dataIntegrityViolation);
            }
            // Reconnect reuses the same gmailConnectionId with a NEW refresh token; evict the
            // cached access token derived from the OLD grant so post-reconnect calls never run on
            // the stale token until its TTL expires.
            gmailApiClientFactory.evictAccessToken(targetMailboxId);
        } finally {
            Arrays.fill(plaintextRefreshTokenBytes, (byte) 0);
        }
    }

    @Transactional
    public void markHistoryLost(UUID tenantId, Long newPointer) {
        connectionRepository
                .findByTenantId(tenantId)
                .ifPresent(
                        connection -> {
                            connection.setLastSyncedHistoryId(newPointer);
                            connection.setIngestionHealth(GmailIngestionHealth.HISTORY_LOST);
                            connectionRepository.save(connection);
                        });
    }

    @Transactional
    public void markWatchUnhealthy(UUID tenantId) {
        connectionRepository
                .findByTenantId(tenantId)
                .ifPresent(
                        connection -> {
                            connection.setIngestionHealth(GmailIngestionHealth.WATCH_UNHEALTHY);
                            connectionRepository.save(connection);
                        });
    }

    @Transactional
    public void recordWatchSuccess(UUID tenantId, Long watchHistoryId, Instant watchExpiresAt) {
        connectionRepository
                .findByTenantId(tenantId)
                .ifPresent(
                        connection -> {
                            connection.setWatchHistoryId(watchHistoryId);
                            if (connection.getLastSyncedHistoryId() == null) {
                                connection.setLastSyncedHistoryId(watchHistoryId);
                            }
                            connection.setWatchExpiresAt(watchExpiresAt);
                            connection.setWatchRenewedAt(Instant.now());
                            connection.setWatchConsecutiveFailures(0);
                            if (connection.getIngestionHealth()
                                    == GmailIngestionHealth.WATCH_UNHEALTHY) {
                                connection.setIngestionHealth(GmailIngestionHealth.HEALTHY);
                            }
                            connectionRepository.save(connection);
                        });
    }

    @Transactional
    public void incrementWatchFailure(UUID tenantId) {
        connectionRepository
                .findByTenantId(tenantId)
                .ifPresent(
                        connection -> {
                            connection.setWatchConsecutiveFailures(
                                    connection.getWatchConsecutiveFailures() + 1);
                            connectionRepository.save(connection);
                        });
    }

    @Transactional
    public void clearForReconnect(UUID tenantId) {
        connectionRepository
                .findByTenantId(tenantId)
                .ifPresent(
                        connection -> {
                            connection.setWatchExpiresAt(null);
                            connection.setWatchHistoryId(null);
                            connection.setLastSyncedHistoryId(null);
                            connection.setWatchConsecutiveFailures(0);
                            connection.setIngestionHealth(GmailIngestionHealth.HEALTHY);
                            connectionRepository.save(connection);
                        });
    }

    private void assertNoActiveDuplicate(UUID tenantId, String googleEmail) {
        boolean duplicateActiveMailboxExists =
                connectionRepository.findByTenantIdOrderByIsPrimaryDesc(tenantId).stream()
                        .anyMatch(
                                gmailConnection ->
                                        gmailConnection.getStatus()
                                                        == GmailConnectionStatus.CONNECTED
                                                && gmailConnection
                                                        .getGoogleEmail()
                                                        .equalsIgnoreCase(googleEmail));
        if (duplicateActiveMailboxExists) {
            throw new DuplicateActiveMailboxException(tenantId);
        }
    }

    private void rethrowDuplicateActiveMailboxIfMatched(
            UUID tenantId, DataIntegrityViolationException dataIntegrityViolation) {
        if (matchesDuplicateActiveEmailConstraint(dataIntegrityViolation)) {
            throw new DuplicateActiveMailboxException(tenantId);
        }
        throw dataIntegrityViolation;
    }

    /**
     * Maps a {@link DataIntegrityViolationException} to the duplicate-active-mailbox case without
     * depending solely on driver-specific reflective accessors. Two independent signals are checked
     * across the cause chain: (1) the reflective {@code getConstraintName()/getServerErrorMessage()}
     * getters (when the PG driver exposes them), and (2) a SQLState {@code 23505}
     * (unique_violation) whose message text mentions the partial-unique index name. Either match is
     * sufficient, so the 409 translation does not break if Boot 4's exception nesting hides the
     * reflective getters.
     */
    private static boolean matchesDuplicateActiveEmailConstraint(Throwable throwable) {
        Throwable currentThrowable = throwable;
        while (currentThrowable != null) {
            if (DUPLICATE_ACTIVE_EMAIL_CONSTRAINT.equals(constraintName(currentThrowable))) {
                return true;
            }
            if (isUniqueViolation(currentThrowable)
                    && messageMentions(currentThrowable, DUPLICATE_ACTIVE_EMAIL_CONSTRAINT)) {
                return true;
            }
            currentThrowable = currentThrowable.getCause();
        }
        return false;
    }

    private static boolean isUniqueViolation(Throwable throwable) {
        if (throwable instanceof java.sql.SQLException sqlException) {
            return "23505".equals(sqlException.getSQLState());
        }
        // PSQLException exposes getSQLState() too, but may not be a SQLException subtype on every
        // classpath shape; fall back to the reflective getter so the check stays driver-agnostic.
        return "23505".equals(invokeStringMethod(throwable, "getSQLState"));
    }

    private static boolean messageMentions(Throwable throwable, String token) {
        String message = throwable.getMessage();
        return message != null && message.contains(token);
    }

    private static String constraintName(Throwable throwable) {
        String directConstraintName = invokeStringMethod(throwable, "getConstraintName");
        if (directConstraintName != null) {
            return directConstraintName;
        }
        Object serverErrorMessage = invokeObjectMethod(throwable, "getServerErrorMessage");
        if (serverErrorMessage == null) {
            return null;
        }
        return invokeStringMethod(serverErrorMessage, "getConstraint");
    }

    private static String invokeStringMethod(Object target, String methodName) {
        Object value = invokeObjectMethod(target, methodName);
        return value instanceof String stringValue ? stringValue : null;
    }

    private static Object invokeObjectMethod(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException | SecurityException ignoredReflectionFailure) {
            return null;
        }
    }
}
