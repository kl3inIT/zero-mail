package com.zeromail.core.gmail.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PubSubDeliveryRepository extends JpaRepository<PubSubDeliveryEntity, UUID> {

    @Transactional
    @Query(
            value =
                    """
            WITH claimed AS (
            UPDATE pubsub_delivery
            SET status = 'PROCESSING',
                locked_until = NOW() + (:lockSeconds * INTERVAL '1 second'),
                attempts = attempts + 1,
                updated_at = NOW(),
                version = version + 1
            WHERE id IN (
                SELECT id
                FROM pubsub_delivery
                WHERE (status = 'PENDING' AND (locked_until IS NULL OR locked_until < NOW()))
                   OR (status = 'PROCESSING' AND locked_until < NOW())
                ORDER BY created_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            RETURNING *
            )
            SELECT *
            FROM claimed
            """,
            nativeQuery = true)
    List<PubSubDeliveryEntity> claimPendingBatch(
            @Param("limit") int limit, @Param("lockSeconds") int lockSeconds);

    @Transactional
    @Query(
            value =
                    """
            WITH claimed AS (
            UPDATE pubsub_delivery
            SET status = 'PROCESSING',
                locked_until = NOW() + (:lockSeconds * INTERVAL '1 second'),
                attempts = attempts + 1,
                updated_at = NOW(),
                version = version + 1
            WHERE id IN (
                SELECT id
                FROM pubsub_delivery
                WHERE tenant_id = :tenantId
                  AND (
                    (status = 'PENDING' AND (locked_until IS NULL OR locked_until < NOW()))
                    OR (status = 'PROCESSING' AND locked_until < NOW())
                  )
                ORDER BY created_at
                LIMIT :limit
                FOR UPDATE SKIP LOCKED
            )
            RETURNING *
            )
            SELECT *
            FROM claimed
            """,
            nativeQuery = true)
    List<PubSubDeliveryEntity> claimPendingBatchForTenant(
            @Param("tenantId") UUID tenantId,
            @Param("limit") int limit,
            @Param("lockSeconds") int lockSeconds);

    @Modifying
    @Query(
            value =
                    """
            INSERT INTO pubsub_delivery
              (id, tenant_id, pubsub_message_id, history_id, payload, status, attempts,
               locked_until, created_at, updated_at, version)
            VALUES
              (:id, :tenantId, :pubsubMessageId, :historyId, CAST(:payload AS jsonb),
               'PENDING', 0, NULL, NOW(), NOW(), 0)
            ON CONFLICT (tenant_id, pubsub_message_id) DO NOTHING
            """,
            nativeQuery = true)
    @Transactional
    int insertPendingIfAbsent(
            @Param("id") UUID id,
            @Param("tenantId") UUID tenantId,
            @Param("pubsubMessageId") String pubsubMessageId,
            @Param("historyId") Long historyId,
            @Param("payload") String payload);

    @Modifying
    @Query(
            "UPDATE PubSubDeliveryEntity d SET d.status = :status, d.lockedUntil = null WHERE d.id = :id")
    @Transactional
    void updateStatus(@Param("id") UUID id, @Param("status") String status);

    @Modifying
    @Query(
            "UPDATE PubSubDeliveryEntity d SET d.status = 'PENDING', d.lockedUntil = :nextAttemptAt WHERE d.id = :id")
    @Transactional
    void releaseForRetry(@Param("id") UUID id, @Param("nextAttemptAt") Instant nextAttemptAt);
}
