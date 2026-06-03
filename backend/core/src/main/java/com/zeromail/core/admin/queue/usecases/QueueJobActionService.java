package com.zeromail.core.admin.queue.usecases;

import com.zeromail.core.admin.audit.domain.AdminAuditAction;
import com.zeromail.core.admin.audit.usecases.AdminAuditWriter;
import com.zeromail.core.admin.auth.AdminContext;
import com.zeromail.core.admin.queue.persistence.lowlevel.ProcessingJobRequeueRepository;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operator-initiated job actions beyond dead-letter requeue: force-retry a FAILED job and cancel a
 * PENDING / stuck-PROCESSING job. Each mutation writes an append-only admin audit row.
 *
 * <p><b>Privacy invariant (T-08-47 + OPS-QUEUE-02):</b> like {@link DeadLetterRequeueService}, this
 * service never reads the stored job body; the audit before/after JSON carries only id, job type,
 * status, and attempt counters.
 *
 * <p>Both operations are idempotent: a call against a job no longer in the eligible state returns 0
 * without writing an audit row.
 */
@Service
public class QueueJobActionService {

    /**
     * Matches the worker's heartbeat grace — a PROCESSING job older than this is presumed stuck.
     */
    private static final Duration STUCK_PROCESSING_GRACE = Duration.ofMinutes(5);

    private final ProcessingJobRequeueRepository processingJobRequeueRepository;
    private final AdminAuditWriter adminAuditWriter;

    public QueueJobActionService(
            ProcessingJobRequeueRepository processingJobRequeueRepository,
            AdminAuditWriter adminAuditWriter) {
        this.processingJobRequeueRepository =
                Objects.requireNonNull(
                        processingJobRequeueRepository,
                        "processingJobRequeueRepository must not be null");
        this.adminAuditWriter =
                Objects.requireNonNull(adminAuditWriter, "adminAuditWriter must not be null");
    }

    /**
     * Force-retries a FAILED job. Returns 1 on success, 0 if the job was not FAILED at call time.
     */
    @Transactional
    public int forceRetry(UUID jobId, String reason, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        UUID targetJobId = Objects.requireNonNull(jobId, "jobId must not be null");

        Map<String, Object> beforeStateMap =
                processingJobRequeueRepository.findMetadata(targetJobId);
        if (beforeStateMap == null || !"FAILED".equals(beforeStateMap.get("status"))) {
            return 0;
        }

        int updatedRows = processingJobRequeueRepository.forceRetryFromFailed(targetJobId);
        if (updatedRows == 0) {
            return 0;
        }

        adminAuditWriter.append(
                AdminAuditAction.JOB_RETRY_FORCED,
                "PROCESSING_JOB",
                targetJobId,
                beforeStateJson(beforeStateMap),
                "{\"status\":\"PENDING\",\"attempts\":0}",
                reason,
                requestIp,
                requestId);
        return updatedRows;
    }

    /**
     * Cancels a PENDING or stuck-PROCESSING job. Returns 1 on success, 0 if the job was not in a
     * cancellable state (active PROCESSING with a fresh heartbeat, or already terminal).
     */
    @Transactional
    public int cancel(UUID jobId, String reason, String requestIp, UUID requestId) {
        AdminContext.currentOrThrow();
        UUID targetJobId = Objects.requireNonNull(jobId, "jobId must not be null");

        Map<String, Object> beforeStateMap =
                processingJobRequeueRepository.findMetadata(targetJobId);
        if (beforeStateMap == null) {
            return 0;
        }
        Object statusBefore = beforeStateMap.get("status");
        if (!"PENDING".equals(statusBefore) && !"PROCESSING".equals(statusBefore)) {
            return 0;
        }

        int updatedRows =
                processingJobRequeueRepository.cancel(targetJobId, STUCK_PROCESSING_GRACE);
        if (updatedRows == 0) {
            // Active PROCESSING with a live heartbeat, or lost the race — idempotent no-op.
            return 0;
        }

        adminAuditWriter.append(
                AdminAuditAction.JOB_CANCELLED,
                "PROCESSING_JOB",
                targetJobId,
                beforeStateJson(beforeStateMap),
                "{\"status\":\"CANCELLED\"}",
                reason,
                requestIp,
                requestId);
        return updatedRows;
    }

    private static String beforeStateJson(Map<String, Object> beforeStateMap) {
        String jobId = String.valueOf(beforeStateMap.get("id"));
        String jobType = jsonQuoted(String.valueOf(beforeStateMap.get("jobType")));
        String statusBefore = jsonQuoted(String.valueOf(beforeStateMap.get("status")));
        int attempts = ((Number) beforeStateMap.get("attempts")).intValue();
        return "{\"jobId\":\""
                + jobId
                + "\",\"jobType\":"
                + jobType
                + ",\"statusBefore\":"
                + statusBefore
                + ",\"attemptsBefore\":"
                + attempts
                + "}";
    }

    private static String jsonQuoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
