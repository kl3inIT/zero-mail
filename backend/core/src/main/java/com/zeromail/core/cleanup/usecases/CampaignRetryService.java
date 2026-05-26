package com.zeromail.core.cleanup.usecases;

import com.zeromail.core.cleanup.domain.UnsubscribeAttemptState;
import com.zeromail.core.cleanup.exception.CampaignNotFoundException;
import com.zeromail.core.cleanup.exception.CampaignRetryConflictException;
import com.zeromail.core.cleanup.persistence.ProcessingJobEntity;
import com.zeromail.core.cleanup.persistence.ProcessingJobRepository;
import com.zeromail.core.cleanup.persistence.UnsubscribeAttemptEntity;
import com.zeromail.core.cleanup.persistence.UnsubscribeAttemptRepository;
import com.zeromail.core.cleanup.persistence.UnsubscribeCampaignEntity;
import com.zeromail.core.cleanup.persistence.UnsubscribeCampaignRepository;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * UNS-06 — Per-sender retry with 409-on-non-terminal idempotency. Resets a {@code FAILED} attempt
 * back to {@code PENDING} and re-queues the parent {@code processing_job} so the worker picks the
 * campaign back up.
 *
 * <p>Idempotency gate: retrying an attempt that is currently {@code OK} (terminal-success) raises
 * {@link CampaignRetryConflictException} so the frontend can render a clean 409 without ambiguous
 * double-archiving. {@code PENDING} / {@code RUNNING} attempts are also rejected — they are still
 * in the worker pipeline and a retry would race with the in-flight processor.
 *
 * <p>Job re-queue path: the parent {@code processing_job} may be in a terminal state ({@code
 * COMPLETED} / {@code FAILED}) by the time the user clicks retry, so the SQL update unconditionally
 * resets status to {@code QUEUED} + clears the lifecycle timestamps (started_at, finished_at,
 * heartbeat_at, failure_reason) and sets {@code next_run_at = NOW()} so the next worker tick picks
 * it up. The worker then iterates over attempts where state = PENDING and only the reset attempt
 * remains to be processed.
 *
 * <p>Privacy invariant (UNS-09): the log line records {@code senderDomain} and {@code campaignId}
 * only — the raw {@code senderEmail} is never written to logs.
 */
@Service
@Transactional
public class CampaignRetryService {

    private static final Logger log = LoggerFactory.getLogger(CampaignRetryService.class);

    private static final String CAMPAIGN_ID_BY_JOB_SQL =
            "SELECT id FROM unsubscribe_campaign WHERE job_id = ? AND tenant_id = ?";

    private static final String REQUEUE_PROCESSING_JOB_SQL =
            "UPDATE processing_job SET status='QUEUED', "
                    + "  next_run_at = NOW(), "
                    + "  heartbeat_at = NULL, "
                    + "  started_at = NULL, "
                    + "  finished_at = NULL, "
                    + "  failure_reason = NULL, "
                    + "  updated_at = NOW() "
                    + "WHERE id = ? AND tenant_id = ?";

    private final UnsubscribeCampaignRepository unsubscribeCampaignRepository;
    private final UnsubscribeAttemptRepository unsubscribeAttemptRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final JdbcTemplate jdbcTemplate;

    public CampaignRetryService(
            UnsubscribeCampaignRepository unsubscribeCampaignRepository,
            UnsubscribeAttemptRepository unsubscribeAttemptRepository,
            ProcessingJobRepository processingJobRepository,
            JdbcTemplate jdbcTemplate) {
        this.unsubscribeCampaignRepository =
                Objects.requireNonNull(
                        unsubscribeCampaignRepository,
                        "unsubscribeCampaignRepository must not be null");
        this.unsubscribeAttemptRepository =
                Objects.requireNonNull(
                        unsubscribeAttemptRepository,
                        "unsubscribeAttemptRepository must not be null");
        this.processingJobRepository =
                Objects.requireNonNull(
                        processingJobRepository, "processingJobRepository must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    public CampaignRetryResult retry(UUID tenantId, UUID jobId, String senderEmail) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(jobId, "jobId must not be null");
        String normalizedSenderEmail = requireText(senderEmail, "senderEmail");

        Optional<ProcessingJobEntity> jobOptional =
                processingJobRepository.findByIdAndTenantId(jobId, tenantId);
        if (jobOptional.isEmpty()) {
            throw new CampaignNotFoundException(jobId);
        }

        List<UUID> campaignIds =
                jdbcTemplate.queryForList(CAMPAIGN_ID_BY_JOB_SQL, UUID.class, jobId, tenantId);
        if (campaignIds.isEmpty()) {
            throw new CampaignNotFoundException(jobId);
        }
        UUID campaignId = campaignIds.get(0);

        UnsubscribeCampaignEntity campaign =
                unsubscribeCampaignRepository
                        .findByIdAndTenantId(campaignId, tenantId)
                        .orElseThrow(() -> new CampaignNotFoundException(campaignId));

        UnsubscribeAttemptEntity attempt =
                unsubscribeAttemptRepository
                        .findByCampaignIdAndSenderEmail(campaign.getId(), normalizedSenderEmail)
                        .orElseThrow(() -> new CampaignNotFoundException(campaign.getId()));

        if (attempt.getState() != UnsubscribeAttemptState.FAILED) {
            throw new CampaignRetryConflictException(
                    campaign.getId(), normalizedSenderEmail, attempt.getState());
        }

        attempt.resetToPending();
        unsubscribeAttemptRepository.save(attempt);

        int requeuedRows = jdbcTemplate.update(REQUEUE_PROCESSING_JOB_SQL, jobId, tenantId);
        if (requeuedRows == 0) {
            // The job row vanished between the findByIdAndTenantId lookup and this UPDATE — could
            // only happen under concurrent admin deletion. Surface as not-found to the caller.
            throw new CampaignNotFoundException(jobId);
        }

        String senderDomain = stripCrlf(extractDomain(normalizedSenderEmail));
        log.info(
                "event=cleanup_campaign_retry_enqueued tenantId={} jobId={} campaignId={} senderDomain={}",
                tenantId,
                jobId,
                campaign.getId(),
                senderDomain);

        return new CampaignRetryResult(campaign.getId(), attempt.getId());
    }

    private static String extractDomain(String senderEmail) {
        int atIndex = senderEmail.lastIndexOf('@');
        if (atIndex < 0 || atIndex == senderEmail.length() - 1) {
            return "unknown";
        }
        return senderEmail.substring(atIndex + 1).toLowerCase(Locale.ROOT);
    }

    private static String requireText(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return text;
    }

    private static String stripCrlf(String value) {
        return value == null ? null : value.replaceAll("[\\r\\n]", "_");
    }

    /** Returned to the controller; identifiers only — no per-sender PII. */
    public record CampaignRetryResult(UUID campaignId, UUID attemptId) {

        public CampaignRetryResult {
            Objects.requireNonNull(campaignId, "campaignId must not be null");
            Objects.requireNonNull(attemptId, "attemptId must not be null");
        }
    }
}
