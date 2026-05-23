package com.zeromail.core.waitlist.persistence;

import com.zeromail.core.waitlist.domain.WaitlistStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaitlistEmailRepository extends JpaRepository<WaitlistEmailEntity, UUID> {

    boolean existsByEmailIgnoreCase(String email);

    Page<WaitlistEmailEntity> findByStatus(WaitlistStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WaitlistEmailEntity w WHERE w.id = :id")
    Optional<WaitlistEmailEntity> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Worker-side claim query. Returns up to {@code limit} APPROVED rows whose {@code
     * invite_next_attempt_at} is either NULL (never attempted) or due. {@code SKIP LOCKED} lets
     * multiple worker instances run concurrently without lock contention even though ShedLock
     * already prevents two cron ticks overlapping.
     */
    @Query(
            value =
                    """
                    SELECT id
                    FROM waitlist_email
                    WHERE status = 'APPROVED'
                      AND (invite_next_attempt_at IS NULL
                           OR invite_next_attempt_at <= :referenceInstant)
                    ORDER BY approved_at ASC
                    LIMIT :limit
                    FOR UPDATE SKIP LOCKED
                    """,
            nativeQuery = true)
    List<UUID> findDueInviteIds(
            @Param("referenceInstant") Instant referenceInstant, @Param("limit") int limit);
}
