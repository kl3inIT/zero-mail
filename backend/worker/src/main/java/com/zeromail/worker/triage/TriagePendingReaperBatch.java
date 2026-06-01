package com.zeromail.worker.triage;

import com.zeromail.core.triage.persistence.TriageAuditEntity;
import com.zeromail.core.triage.persistence.TriageAuditRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Last-resort backstop for PENDING audit rows no delivery will revisit. */
@Component
public class TriagePendingReaperBatch {

    private static final Logger log = LoggerFactory.getLogger(TriagePendingReaperBatch.class);
    private static final Duration SAGA_RETRY_LEASE_WINDOW = Duration.parse("PT2M");
    private static final String FAILURE_REASON = "STUCK_PENDING_REAPED_ABANDONED";

    private final TriageAuditRepository triageAuditRepository;
    private final Clock clock;
    private final Duration abandonedThreshold;

    public TriagePendingReaperBatch(
            TriageAuditRepository triageAuditRepository,
            Clock clock,
            @Value("${zero-mail.triage.pending-abandoned-threshold:PT30M}")
                    Duration abandonedThreshold) {
        if (abandonedThreshold == null
                || abandonedThreshold.compareTo(SAGA_RETRY_LEASE_WINDOW) <= 0) {
            throw new IllegalArgumentException(
                    "pending abandoned threshold must be greater than the saga retry lease window");
        }
        this.triageAuditRepository = triageAuditRepository;
        this.clock = clock;
        this.abandonedThreshold = abandonedThreshold;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public ReaperBatchResult reapStuckPendingOnce(int batchLimit) {
        if (batchLimit <= 0) {
            throw new IllegalArgumentException("batchLimit must be positive");
        }

        Instant cutoff = clock.instant().minus(abandonedThreshold);
        List<TriageAuditEntity> stuckPendingAudits =
                triageAuditRepository.findStuckPendingForReaping(
                        cutoff, PageRequest.of(0, batchLimit));

        int reapedCount = 0;
        for (TriageAuditEntity triageAudit : stuckPendingAudits) {
            int failedRowCount =
                    triageAuditRepository.markFailed(
                            triageAudit.getAuditId(), triageAudit.getTenantId(), FAILURE_REASON);
            if (failedRowCount == 1) {
                reapedCount++;
                log.info(
                        "event=triage_pending_reaped_one tenantId={} auditId={} lastLeaseOwner={} lastAttemptAt={}",
                        triageAudit.getTenantId(),
                        triageAudit.getAuditId(),
                        triageAudit.getLeaseOwner(),
                        triageAudit.getLastAttemptAt());
            }
        }
        return new ReaperBatchResult(stuckPendingAudits.size(), reapedCount);
    }

    public record ReaperBatchResult(int selectedCount, int reapedCount) {}
}
