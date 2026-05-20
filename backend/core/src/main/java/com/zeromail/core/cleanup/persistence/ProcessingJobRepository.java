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
     * Skeleton for the worker pickup query (D-02). Returns the id of a single QUEUED job whose
     * {@code next_run_at} has elapsed, holding a row-level lock so concurrent workers skip past it
     * via {@code SKIP LOCKED}. Plan 04 / 06 will wire this into {@code
     * ProcessingJobWorker.claimNextJob()} inside a transaction that immediately marks the row
     * RUNNING + records the lease.
     */
    @Query(
            value =
                    "SELECT id FROM processing_job "
                            + "WHERE status = 'QUEUED' AND next_run_at <= NOW() "
                            + "ORDER BY created_at "
                            + "LIMIT 1 FOR UPDATE SKIP LOCKED",
            nativeQuery = true)
    Optional<UUID> claimQueuedJob();

    Optional<ProcessingJobEntity> findByIdAndTenantId(UUID jobId, UUID tenantId);

    /**
     * Reaper sweep (D-03): every RUNNING job whose heartbeat is older than {@code cutoff} is
     * re-queued and its attempt counter incremented. Plan 04 wires this into a scheduled task in
     * the worker subproject.
     */
    @Modifying
    @Transactional
    @Query(
            value =
                    "UPDATE processing_job "
                            + "SET status='QUEUED', "
                            + "    attempts = attempts + 1, "
                            + "    heartbeat_at = NULL, "
                            + "    updated_at = NOW(), "
                            + "    next_run_at = NOW() "
                            + "WHERE status = 'RUNNING' "
                            + "  AND heartbeat_at < :cutoff",
            nativeQuery = true)
    int markStaleRunningJobsAsQueued(@Param("cutoff") Instant cutoff);
}
