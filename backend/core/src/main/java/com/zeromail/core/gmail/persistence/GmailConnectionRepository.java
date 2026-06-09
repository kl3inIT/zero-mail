package com.zeromail.core.gmail.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface GmailConnectionRepository extends JpaRepository<GmailConnectionEntity, UUID> {

    /**
     * Single-row compatibility accessor for legacy "operate on the tenant's active mailbox" callers.
     *
     * <p>Migration 119 dropped the per-tenant unique constraint, so a tenant may now own several
     * {@code gmail_connections} rows. A plain derived {@code findByTenantId} would execute as a
     * single-result query and throw {@code NonUniqueResultException} the moment a second mailbox
     * exists. This shim instead resolves the tenant's <em>primary</em> mailbox deterministically:
     * primary first, then any CONNECTED row, then most-recently connected, then by id. New callers
     * that need to act on a specific mailbox must use {@link #findByIdAndTenantId} /
     * {@link #findByTenantIdOrderByIsPrimaryDesc}; this method only keeps the historic single-row
     * code paths working against the now multi-row table.
     */
    default Optional<GmailConnectionEntity> findByTenantId(UUID tenantId) {
        return findPrimaryMailboxCandidatesByTenantId(tenantId, Limit.of(1)).stream().findFirst();
    }

    @Query(
            """
            SELECT c FROM GmailConnectionEntity c
            WHERE c.tenantId = :tenantId
            ORDER BY c.isPrimary DESC,
                     CASE WHEN c.status = com.zeromail.core.gmail.domain.GmailConnectionStatus.CONNECTED THEN 0 ELSE 1 END ASC,
                     c.connectedAt DESC NULLS LAST,
                     c.id ASC
            """)
    List<GmailConnectionEntity> findPrimaryMailboxCandidatesByTenantId(
            @Param("tenantId") UUID tenantId, Limit limit);

    Optional<GmailConnectionEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<GmailConnectionEntity> findByTenantIdOrderByIsPrimaryDesc(UUID tenantId);

    Optional<GmailConnectionEntity> findByGoogleEmailIgnoreCase(String googleEmail);

    @Query(
            value =
                    """
            SELECT * FROM gmail_connections
            WHERE status = 'CONNECTED'
              AND (watch_expires_at IS NULL OR watch_expires_at < NOW() + INTERVAL '24 hours')
            ORDER BY watch_renewed_at NULLS FIRST
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """,
            nativeQuery = true)
    @Transactional
    List<GmailConnectionEntity> findConnectionsNeedingWatchRenewal(@Param("limit") int limit);

    @Modifying
    @Query(
            "UPDATE GmailConnectionEntity c SET c.lastSyncedHistoryId = :newId "
                    + "WHERE c.tenantId = :tenantId AND "
                    + "(c.lastSyncedHistoryId IS NULL OR c.lastSyncedHistoryId < :newId)")
    @Transactional
    int updateLastSyncedHistoryIdMonotonic(
            @Param("tenantId") UUID tenantId, @Param("newId") Long newId);
}
