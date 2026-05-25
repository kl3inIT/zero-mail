package com.zeromail.core.cleanup.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJobEntity, UUID> {

    /**
     * Worker pickup query (Phase 8 D-02). Returns the id of a single PENDING job whose {@code
     * next_run_at} has elapsed, holding a row-level lock so concurrent workers skip past it via
     * {@code SKIP LOCKED}. Status vocabulary follows main's existing CHECK constraint ({@code
     * PENDING / PROCESSING / COMPLETED / FAILED / DEAD_LETTER}).
     */
    @Query(
            value =
                    "SELECT id FROM processing_job "
                            + "WHERE status = 'PENDING' AND next_run_at <= NOW() "
                            + "ORDER BY created_at "
                            + "LIMIT 1 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Optional<UUID> claimPendingJob();

    Optional<ProcessingJobEntity> findByIdAndTenantId(UUID jobId, UUID tenantId);

    /**
     * Reaper sweep (Phase 8 D-03): every PROCESSING job whose heartbeat is older than {@code
     * cutoff} is re-queued (status → PENDING) and its attempt counter incremented. Wired into a
     * scheduled batch in {@code backend/worker/.../ProcessingJobReaperBatch}.
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    "UPDATE processing_job "
                            + "SET status='PENDING', "
                            + "    attempts = attempts + 1, "
                            + "    heartbeat_at = NULL, "
                            + "    updated_at = NOW(), "
                            + "    next_run_at = NOW() "
                            + "WHERE status = 'PROCESSING' "
                            + "  AND heartbeat_at < :cutoff",
            nativeQuery = true)
    int markStaleProcessingJobsAsPending(@Param("cutoff") Instant cutoff);
}
